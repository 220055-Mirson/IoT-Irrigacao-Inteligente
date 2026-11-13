package mz.grupo4.irrigacao.dto;

// Mensagem recebida no tópico /eventos.
// evento: "INICIO" ou "FIM"; modo: "AUTO" ou "MANUAL".
public record IrrigationEventMessage(
        String evento,
        String motivo,
        String modo) {
}