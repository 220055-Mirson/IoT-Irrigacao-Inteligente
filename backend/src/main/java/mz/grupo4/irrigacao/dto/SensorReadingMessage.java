package mz.grupo4.irrigacao.dto;

// Mensagem recebida no tópico /leituras.
// Os nomes dos campos são os mesmos que o ESP32 envia.
public record SensorReadingMessage(
        Float soilMoisture,
        Float temperature,
        Float airHumidity,
        Boolean pumpOn,
        String operatingMode) {
}