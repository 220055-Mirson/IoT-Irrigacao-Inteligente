#include <Arduino.h>

/*
 * Sistema de Irrigação Inteligente – Grupo 4 (DSIo 2026)
 * ESP32 + humidade do solo + DHT22 + relé (bomba) + MQTT
 *
 * Ficha, requisitos do Grupo 4:
 *  - mede a humidade do solo e uma variável ambiental (temperatura e humidade do ar)
 *  - aciona a bomba através de um relé
 *  - modo automático e manual, com limites configuráveis
 *  - envia leituras e eventos de irrigação (data/hora são atribuídas pelo backend)
 *
 * Simulação no Wokwi: o potenciómetro representa o sensor de humidade do solo.
 */

#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// ======================= PINOS =======================
const int PINO_SOLO = 34;                 // entrada analógica ADC1 (funciona com Wi-Fi ligado)
const int PINO_DHT  = 15;                 // dados do DHT22
const int PINO_RELE = 26;                 // entrada IN do relé
const bool RELE_ACTIVO_EM_ALTO = true;    // se o LED ficar invertido, mudar para false

DHT dht(PINO_DHT, DHT22);

// ======================= WI-FI E MQTT =======================
const char* WIFI_SSID  = "Wokwi-GUEST";   // rede virtual do simulador
const char* WIFI_SENHA = "";
const int   WIFI_CANAL = 6;               // acelera a ligação no Wokwi

const char* MQTT_SERVIDOR = "broker.hivemq.com";   // broker público
const int   MQTT_PORTA    = 1883;
const char* PREFIXO_TOPICOS = "dsio/grupo4/irrigacao";

String topicoLeituras;   // ESP32 -> servidor: leituras dos sensores
String topicoEventos;    // ESP32 -> servidor: início e fim de cada irrigação
String topicoEstado;     // ESP32 -> servidor: "online" / "offline"
String topicoComando;    // servidor -> ESP32: modo, bomba manual e limites

WiFiClient clienteWifi;
PubSubClient clienteMqtt(clienteWifi);

// ======================= CONFIGURAÇÃO (valores iniciais do schema.sql) =======================
float limiteLigar = 30.0;          // moisture_limit: abaixo disto a bomba pode ligar
float limitePara = 45.0;           // moisture_stop_limit: a partir daqui a bomba desliga
float limiteTemperatura = 20.0;    // temperature_limit: só irriga acima deste valor
String modoFuncionamento = "AUTO"; // "AUTO" ou "MANUAL"

bool bombaLigada = false;

// ======================= TEMPOS =======================
const unsigned long INTERVALO_CICLO_MS     = 5000;   // leitura + regra + envio
const unsigned long INTERVALO_RECONEXAO_MS = 5000;   // nova tentativa de Wi-Fi / MQTT
unsigned long ultimoCiclo = 0;
unsigned long ultimaTentativaWifi = 0;
unsigned long ultimaTentativaMqtt = 0;

// ======================= FUNÇÕES AUXILIARES =======================

// Arredonda a uma casa decimal para a mensagem ficar mais pequena
float arredondar(float valor) {
  return roundf(valor * 10.0f) / 10.0f;
}

// Liga ou desliga fisicamente o relé
void definirRele(bool ligado) {
  digitalWrite(PINO_RELE, (ligado == RELE_ACTIVO_EM_ALTO) ? HIGH : LOW);
}

// Lê o sensor do solo e converte para percentagem (0 a 100)
float lerHumidadeSolo() {
  int bruto = analogRead(PINO_SOLO);           // 0 a 4095
  if (bruto < 0 || bruto > 4095) return NAN;   // leitura fora do intervalo
  return bruto * 100.0f / 4095.0f;
}

// ======================= MQTT: ENVIO =======================

// Eventos que não puderam ser enviados ficam guardados até haver ligação
const int MAX_EVENTOS_PENDENTES = 10;
String eventosPendentes[MAX_EVENTOS_PENDENTES];
int numeroEventosPendentes = 0;

// Envia o início ou o fim de uma irrigação (o backend regista data, hora e motivo)
void publicarEvento(const char* evento, const char* motivo) {
  StaticJsonDocument<256> doc;
  doc["evento"] = evento;                 // "INICIO" ou "FIM"
  doc["motivo"] = motivo;
  doc["modo"]   = modoFuncionamento;

  char mensagem[256];
  serializeJson(doc, mensagem, sizeof(mensagem));

  if (clienteMqtt.connected()) {
    clienteMqtt.publish(topicoEventos.c_str(), mensagem);
  } else if (numeroEventosPendentes < MAX_EVENTOS_PENDENTES) {
    eventosPendentes[numeroEventosPendentes++] = mensagem;   // guarda para enviar depois
    Serial.println("[MQTT] Sem ligacao: evento guardado");
  } else {
    Serial.println("[MQTT] Sem ligacao: fila cheia, evento perdido");
  }
  Serial.printf("[EVENTO] %s - %s\n", evento, motivo);
}

// Envia os eventos guardados quando a ligação volta
void enviarEventosPendentes() {
  for (int i = 0; i < numeroEventosPendentes; i++) {
    clienteMqtt.publish(topicoEventos.c_str(), eventosPendentes[i].c_str());
  }
  if (numeroEventosPendentes > 0) {
    Serial.printf("[MQTT] %d evento(s) guardado(s) enviado(s)\n", numeroEventosPendentes);
  }
  numeroEventosPendentes = 0;
}

// Envia uma leitura. Os nomes dos campos coincidem com a entidade SensorReading do backend
void publicarLeitura(float solo, float temperatura, float humidadeAr) {
  StaticJsonDocument<256> doc;
  doc["soilMoisture"]  = arredondar(solo);
  doc["temperature"]   = arredondar(temperatura);
  doc["airHumidity"]   = arredondar(humidadeAr);
  doc["pumpOn"]        = bombaLigada;
  doc["operatingMode"] = modoFuncionamento;

  char mensagem[256];
  serializeJson(doc, mensagem, sizeof(mensagem));

  if (clienteMqtt.connected()) {
    clienteMqtt.publish(topicoLeituras.c_str(), mensagem);
    Serial.printf("[MQTT] Leitura enviada: %s\n", mensagem);
  } else {
    Serial.println("[MQTT] Sem ligacao: leitura nao enviada");
  }
}

// ======================= BOMBA =======================

void ligarBomba(const char* motivo) {
  if (bombaLigada) return;
  bombaLigada = true;
  definirRele(true);
  publicarEvento("INICIO", motivo);
}

void desligarBomba(const char* motivo) {
  if (!bombaLigada) return;
  bombaLigada = false;
  definirRele(false);
  publicarEvento("FIM", motivo);
}

// ======================= REGRA AUTOMÁTICA =======================
// SE humidade do solo < limiteLigar E temperatura > limiteTemperatura, ENTÃO liga a bomba.
// SE a bomba está ligada E humidade do solo >= limitePara, ENTÃO desliga a bomba.
void aplicarRegraAutomatica(float solo, float temperatura, bool temperaturaValida) {
  if (modoFuncionamento != "AUTO") return;   // no modo manual a regra não actua

  if (!bombaLigada) {
    // Sem temperatura válida não se liga a bomba (leitura de segurança)
    if (temperaturaValida && solo < limiteLigar && temperatura > limiteTemperatura) {
      char motivo[140];
      snprintf(motivo, sizeof(motivo),
               "Humidade do solo (%.1f%%) abaixo do limite (%.1f%%) e temperatura (%.1f C) acima de %.1f C",
               solo, limiteLigar, temperatura, limiteTemperatura);
      ligarBomba(motivo);
    }
  } else {
    if (solo >= limitePara) {
      desligarBomba("Humidade do solo atingiu o limite de paragem");
    }
  }
}

// ======================= MQTT: RECEPÇÃO =======================
// Mensagem de comando (JSON). Todos os campos são opcionais:
// {"modo":"MANUAL","bomba":true,"limiteLigar":30,"limitePara":45,"limiteTemperatura":20}
void aoReceberMensagem(char* topico, byte* conteudo, unsigned int tamanho) {
  StaticJsonDocument<256> doc;
  DeserializationError erro = deserializeJson(doc, conteudo, tamanho);
  if (erro) {
    Serial.printf("[MQTT] JSON invalido: %s\n", erro.c_str());
    return;
  }

  // 1) Limites configuráveis (só se o conjunto for válido, como no backend)
  float novoLigar = doc["limiteLigar"] | limiteLigar;
  float novoPara  = doc["limitePara"] | limitePara;
  float novaTemp  = doc["limiteTemperatura"] | limiteTemperatura;
  if (novoLigar >= 0 && novoPara <= 100 && novoPara > novoLigar &&
      novaTemp >= -40 && novaTemp <= 80) {
    limiteLigar = novoLigar;
    limitePara = novoPara;
    limiteTemperatura = novaTemp;
  } else {
    Serial.println("[MQTT] Limites invalidos: ignorados");
  }

  // 2) Modo de funcionamento
  const char* novoModo = doc["modo"] | "";
  if (strcmp(novoModo, "AUTO") == 0 || strcmp(novoModo, "MANUAL") == 0) {
    modoFuncionamento = novoModo;
  }

  // 3) Bomba manual (só tem efeito no modo manual)
  if (modoFuncionamento == "MANUAL" && !doc["bomba"].isNull()) {
    bool ligar = doc["bomba"];
    if (ligar) ligarBomba("Accionamento manual");
    else       desligarBomba("Paragem manual");
  }

  Serial.printf("[CONFIG] modo=%s ligar<%.1f parar>=%.1f temp>%.1f\n",
                modoFuncionamento.c_str(), limiteLigar, limitePara, limiteTemperatura);
}

// ======================= LIGAÇÕES (sem bloquear o programa) =======================
// Se o Wi-Fi ou o MQTT cair, o ESP32 continua a aplicar a regra localmente
// e tenta reconectar de tempos a tempos.
void garantirLigacoes() {
  unsigned long agora = millis();

  if (WiFi.status() != WL_CONNECTED) {
    if (agora - ultimaTentativaWifi >= INTERVALO_RECONEXAO_MS) {
      ultimaTentativaWifi = agora;
      Serial.println("[WIFI] Sem ligacao. A tentar reconectar...");
      WiFi.disconnect(true);                       // no Wokwi é preciso limpar a configuração
      WiFi.begin(WIFI_SSID, WIFI_SENHA, WIFI_CANAL);
    }
    return;
  }

  if (!clienteMqtt.connected() && agora - ultimaTentativaMqtt >= INTERVALO_RECONEXAO_MS) {
    ultimaTentativaMqtt = agora;
    Serial.println("[MQTT] A ligar ao broker...");
    String idCliente = "esp32-grupo4-" + String(random(0xffff), HEX);

    // Mensagem de "última vontade": o broker publica "offline" se o ESP32 desaparecer
    if (clienteMqtt.connect(idCliente.c_str(), NULL, NULL,
                            topicoEstado.c_str(), 1, true, "offline")) {
      Serial.println("[MQTT] Ligado");
      clienteMqtt.subscribe(topicoComando.c_str());
      clienteMqtt.publish(topicoEstado.c_str(), "online", true);
      enviarEventosPendentes();
    } else {
      Serial.printf("[MQTT] Falhou (codigo %d)\n", clienteMqtt.state());
    }
  }
}

// ======================= CICLO PRINCIPAL DE LEITURA =======================
void executarCiclo() {
  float solo       = lerHumidadeSolo();
  float temperatura = dht.readTemperature();
  float humidadeAr  = dht.readHumidity();

  // Tratamento de leituras inválidas
  bool soloValido        = !isnan(solo);
  bool temperaturaValida = !isnan(temperatura) && temperatura >= -40 && temperatura <= 80;
  bool humidadeArValida  = !isnan(humidadeAr) && humidadeAr >= 0 && humidadeAr <= 100;

  if (!soloValido)        Serial.println("[ERRO] Leitura invalida do sensor de solo");
  if (!temperaturaValida) Serial.println("[ERRO] Leitura invalida da temperatura (DHT22)");
  if (!humidadeArValida)  Serial.println("[ERRO] Leitura invalida da humidade do ar (DHT22)");

  if (soloValido) {
    aplicarRegraAutomatica(solo, temperatura, temperaturaValida);
  }

  // Só se envia uma leitura quando os três valores são válidos
  if (soloValido && temperaturaValida && humidadeArValida) {
    Serial.printf("[LEITURA] solo=%.1f%% temp=%.1fC ar=%.1f%% bomba=%s modo=%s\n",
                  solo, temperatura, humidadeAr,
                  bombaLigada ? "ON" : "OFF", modoFuncionamento.c_str());
    publicarLeitura(solo, temperatura, humidadeAr);
  }
}

// ======================= SETUP E LOOP =======================
void setup() {
  Serial.begin(115200);

  pinMode(PINO_RELE, OUTPUT);
  definirRele(false);          // bomba desligada ao arrancar
  dht.begin();

  topicoLeituras = String(PREFIXO_TOPICOS) + "/leituras";
  topicoEventos  = String(PREFIXO_TOPICOS) + "/eventos";
  topicoEstado   = String(PREFIXO_TOPICOS) + "/estado";
  topicoComando  = String(PREFIXO_TOPICOS) + "/comando";

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_SENHA, WIFI_CANAL);

  clienteMqtt.setServer(MQTT_SERVIDOR, MQTT_PORTA);
  clienteMqtt.setCallback(aoReceberMensagem);
  clienteMqtt.setBufferSize(512);

  Serial.println("Sistema de Irrigacao Inteligente - Grupo 4");
}

void loop() {
  garantirLigacoes();

  if (clienteMqtt.connected()) {
    clienteMqtt.loop();        // recebe comandos
  }

  if (millis() - ultimoCiclo >= INTERVALO_CICLO_MS) {
    ultimoCiclo = millis();
    executarCiclo();
  }
}