package mz.grupo4.irrigacao.dto;

// Corpo do pedido POST /api/pump (só no modo manual).
public record PumpRequest(Boolean pumpOn) {
}