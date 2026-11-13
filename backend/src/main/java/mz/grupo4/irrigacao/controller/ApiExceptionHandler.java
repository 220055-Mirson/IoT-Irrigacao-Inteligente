package mz.grupo4.irrigacao.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

// Transforma as excepções dos serviços em respostas JSON: {"erro": "mensagem"}
@RestControllerAdvice
public class ApiExceptionHandler {

    // Dados inválidos (limites, modo, campos em falta)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> pedidoInvalido(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", e.getMessage()));
    }

    // Pedido que não pode ser feito no estado actual (por exemplo, bomba em modo AUTO)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> estadoInvalido(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
    }

    // Corpo do pedido que não é JSON válido
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> corpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("erro", "O corpo do pedido não é um JSON válido."));
    }

    // Erros com código próprio (por exemplo, 503 quando o MQTT está desligado)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> comEstado(ResponseStatusException e) {
        String motivo = e.getReason() == null ? "Erro." : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("erro", motivo));
    }
}