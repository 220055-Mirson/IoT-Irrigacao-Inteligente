package mz.grupo4.irrigacao.dto;

// Resposta de GET /api/status: resumo do estado do sistema.
//  esp32: "online", "offline" ou "desconhecido"
//  operatingMode: modo configurado ("AUTO" ou "MANUAL")
//  pumpOn: estado da bomba na última leitura (null se ainda não há leituras)
//  irrigationActive: existe uma irrigação por terminar
public record StatusResponse(
        String esp32,
        String operatingMode,
        Boolean pumpOn,
        boolean irrigationActive) {
}