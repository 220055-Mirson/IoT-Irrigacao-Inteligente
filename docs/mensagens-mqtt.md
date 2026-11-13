# Mensagens MQTT – Sistema de Irrigação Inteligente (Grupo 4)

Broker: `broker.hivemq.com`, porta `1883` (broker público, usado na simulação).

Todos os tópicos começam pelo prefixo `dsio/grupo4/irrigacao`.

| Tópico | Sentido | Conteúdo |
|---|---|---|
| `/leituras` | ESP32 → servidor | `{"soilMoisture":..,"temperature":..,"airHumidity":..,"pumpOn":..,"operatingMode":".."}` |
| `/eventos` | ESP32 → servidor | `{"evento":"INICIO","motivo":"..","modo":"AUTO"}` (ou `"FIM"`) |
| `/estado` | ESP32 → servidor | `online` ou `offline` (mensagem retida; `offline` é enviada pelo broker se o ESP32 desaparecer) |
| `/comando` | servidor → ESP32 | `{"modo":"MANUAL","bomba":true,"limiteLigar":30,"limitePara":45,"limiteTemperatura":20}` (todos os campos opcionais) |

## Notas

- Os campos de `/leituras` coincidem com a entidade `SensorReading` do backend.
- O campo `modo` e `operatingMode` só aceita `AUTO` ou `MANUAL`.
- O campo `bomba` só tem efeito quando o modo é `MANUAL`.
- Os limites só são aceites se `limitePara` > `limiteLigar`, ambos entre 0 e 100, e a temperatura entre -40 e 80.
- A data e a hora de cada leitura e de cada irrigação são atribuídas pelo backend.
- Se o MQTT estiver desligado quando uma irrigação começa ou termina, o ESP32 guarda o evento (máximo de 10) e envia-o quando a ligação volta. Nesse caso a hora registada é a do envio.

## Como testar com um cliente MQTT

1. Corre a simulação no Wokwi (VS Code: F1 → Wokwi: Start Simulator).
2. Abre um cliente MQTT no browser (por exemplo o cliente web da HiveMQ) e liga-o a `broker.hivemq.com`.
3. Subscreve o tópico `dsio/grupo4/irrigacao/#` para ver todas as mensagens.
4. Publica em `dsio/grupo4/irrigacao/comando` uma das mensagens abaixo.
5. Confirma o resultado no terminal do Wokwi e no LED que representa a bomba.

| Mensagem em `/comando` | Resultado esperado |
|---|---|
| `{"modo":"MANUAL","bomba":false}` | Modo passa a MANUAL e a bomba desliga (evento `FIM`, motivo "Paragem manual") |
| `{"modo":"MANUAL","bomba":true}` | A bomba liga (evento `INICIO`, motivo "Accionamento manual") |
| `{"modo":"AUTO"}` | Volta ao modo automático; a regra volta a decidir |
| `{"limiteLigar":25,"limitePara":50,"limiteTemperatura":22}` | Novos limites aplicados |