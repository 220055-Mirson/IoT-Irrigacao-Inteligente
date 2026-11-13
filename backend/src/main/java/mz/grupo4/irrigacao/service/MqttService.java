package mz.grupo4.irrigacao.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import mz.grupo4.irrigacao.dto.CommandMessage;
import mz.grupo4.irrigacao.dto.IrrigationEventMessage;
import mz.grupo4.irrigacao.dto.SensorReadingMessage;
import mz.grupo4.irrigacao.model.SensorReading;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Comunicação com o ESP32 através do broker MQTT.
//  - Recebe  /leituras, /eventos e /estado e guarda os dados na base de dados.
//  - Envia   /comando (modo, bomba manual e limites).
@Service
public class MqttService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttService.class);

    private static final int QOS = 1;
    private static final int SEGUNDOS_ENTRE_TENTATIVAS = 5;

    private final SensorReadingService leituras;
    private final IrrigationService irrigacoes;

    private final String broker;
    private final String topicoLeituras;
    private final String topicoEventos;
    private final String topicoEstado;
    private final String topicoComando;

    // Jackson 3: o mapper é imutável e criado pelo builder
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ScheduledExecutorService agendador =
            Executors.newSingleThreadScheduledExecutor();

    // Último estado recebido do ESP32 ("online" ou "offline")
    private final AtomicReference<String> estadoEsp32 =
            new AtomicReference<>("desconhecido");

    private MqttClient cliente;

    public MqttService(SensorReadingService leituras,
                       IrrigationService irrigacoes,
                       @Value("${mqtt.broker}") String broker,
                       @Value("${mqtt.prefixo}") String prefixo) {
        this.leituras = leituras;
        this.irrigacoes = irrigacoes;
        this.broker = broker;
        this.topicoLeituras = prefixo + "/leituras";
        this.topicoEventos = prefixo + "/eventos";
        this.topicoEstado = prefixo + "/estado";
        this.topicoComando = prefixo + "/comando";
    }

    // ---------------------------------------------------------------
    // Ligação ao broker
    // ---------------------------------------------------------------

    @PostConstruct
    public void iniciar() {
        try {
            // O identificador tem de ser único em todo o broker público
            String idCliente = "backend-grupo4-" + UUID.randomUUID().toString().substring(0, 8);
            cliente = new MqttClient(broker, idCliente, new MemoryPersistence());
            cliente.setCallback(this);
        } catch (MqttException e) {
            log.error("Erro ao criar o cliente MQTT: {}", e.getMessage());
            return;
        }
        tentarLigar();
    }

    // Se o broker não estiver acessível, tenta de novo sem impedir a aplicação de arrancar
    private void tentarLigar() {
        try {
            MqttConnectOptions opcoes = new MqttConnectOptions();
            opcoes.setAutomaticReconnect(true);   // reconecta sozinho se a ligação cair
            opcoes.setCleanSession(true);
            opcoes.setConnectionTimeout(10);
            cliente.connect(opcoes);
        } catch (MqttException e) {
            log.warn("Sem ligacao ao broker MQTT ({}). Nova tentativa em {} s.",
                    e.getMessage(), SEGUNDOS_ENTRE_TENTATIVAS);
            agendador.schedule(this::tentarLigar, SEGUNDOS_ENTRE_TENTATIVAS, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void terminar() {
        agendador.shutdownNow();
        try {
            if (cliente != null && cliente.isConnected()) {
                cliente.disconnect();
            }
            if (cliente != null) {
                cliente.close();
            }
        } catch (MqttException e) {
            log.warn("Erro ao fechar a ligacao MQTT: {}", e.getMessage());
        }
    }

    // Chamado depois de cada ligação (a primeira e as reconexões):
    // é aqui que se subscrevem os tópicos
    @Override
    public void connectComplete(boolean reconexao, String servidor) {
        log.info("Ligado ao broker MQTT {} (reconexao: {})", servidor, reconexao);
        try {
            cliente.subscribe(
                    new String[] { topicoLeituras, topicoEventos, topicoEstado },
                    new int[] { QOS, QOS, QOS });
            log.info("Subscrito: {}, {}, {}", topicoLeituras, topicoEventos, topicoEstado);
        } catch (MqttException e) {
            log.error("Erro ao subscrever os topicos: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable causa) {
        log.warn("Ligacao MQTT perdida: {}", causa == null ? "?" : causa.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Não é preciso fazer nada
    }

    // ---------------------------------------------------------------
    // Recepção de mensagens
    // ---------------------------------------------------------------

    // Uma excepção aqui dentro fecharia a ligação ao broker,
    // por isso qualquer erro é apanhado e registado.
    @Override
    public void messageArrived(String topico, MqttMessage mensagem) {
        String conteudo = new String(mensagem.getPayload(), StandardCharsets.UTF_8);
        try {
            if (topico.equals(topicoLeituras)) {
                tratarLeitura(conteudo);
            } else if (topico.equals(topicoEventos)) {
                tratarEvento(conteudo);
            } else if (topico.equals(topicoEstado)) {
                tratarEstado(conteudo);
            }
        } catch (Exception e) {
            log.warn("Mensagem ignorada em {} ({}): {}", topico, conteudo, e.getMessage());
        }
    }

    private void tratarLeitura(String conteudo) {
        SensorReadingMessage msg = jsonMapper.readValue(conteudo, SensorReadingMessage.class);

        SensorReading leitura = new SensorReading();
        leitura.setSoilMoisture(msg.soilMoisture());
        leitura.setTemperature(msg.temperature());
        leitura.setAirHumidity(msg.airHumidity());
        leitura.setPumpOn(msg.pumpOn());
        leitura.setOperatingMode(msg.operatingMode());

        // A validação (limites, modo) é feita pelo SensorReadingService
        SensorReading guardada = leituras.saveReading(leitura);
        log.info("Leitura guardada (id={}): solo={} temp={} ar={} bomba={} modo={}",
                guardada.getId(), guardada.getSoilMoisture(), guardada.getTemperature(),
                guardada.getAirHumidity(), guardada.getPumpOn(), guardada.getOperatingMode());
    }

    private void tratarEvento(String conteudo) {
        IrrigationEventMessage msg = jsonMapper.readValue(conteudo, IrrigationEventMessage.class);

        if ("INICIO".equals(msg.evento())) {
            irrigacoes.startIrrigation(msg.motivo(), msg.modo());
            log.info("Irrigacao iniciada ({}): {}", msg.modo(), msg.motivo());
        } else if ("FIM".equals(msg.evento())) {
            irrigacoes.finishIrrigation();
            log.info("Irrigacao terminada: {}", msg.motivo());
        } else {
            log.warn("Evento desconhecido: {}", msg.evento());
        }
    }

    private void tratarEstado(String conteudo) {
        String estado = conteudo.trim();
        estadoEsp32.set(estado);
        log.info("Estado do ESP32: {}", estado);
    }

    // ---------------------------------------------------------------
    // Envio de comandos e consulta do estado
    // ---------------------------------------------------------------

    // Envia um comando ao ESP32 (será usado pela API REST no Passo 5)
    public void publicarComando(CommandMessage comando) {
        if (cliente == null || !cliente.isConnected()) {
            throw new IllegalStateException("Sem ligacao ao broker MQTT.");
        }
        try {
            String json = jsonMapper.writeValueAsString(comando);
            cliente.publish(topicoComando, new MqttMessage(json.getBytes(StandardCharsets.UTF_8)));
            log.info("Comando enviado: {}", json);
        } catch (MqttException e) {
            throw new IllegalStateException("Falha ao enviar o comando: " + e.getMessage(), e);
        }
    }

    // "online", "offline" ou "desconhecido" (ainda não chegou nenhuma mensagem)
    public String getEstadoEsp32() {
        return estadoEsp32.get();
    }
}