package mz.grupo4.irrigacao.dto;

// Corpo do pedido PUT /api/config.
// Os nomes dos campos são os da entidade SystemConfig.
public record ConfigRequest(
        Float moistureLimit,
        Float moistureStopLimit,
        Float temperatureLimit,
        String operatingMode) {
}