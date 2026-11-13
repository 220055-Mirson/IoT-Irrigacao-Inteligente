package mz.grupo4.irrigacao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Mensagem enviada para o tópico /comando.
// Todos os campos são opcionais: os que estiverem a null não são enviados.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandMessage(
        String modo,
        Boolean bomba,
        Float limiteLigar,
        Float limitePara,
        Float limiteTemperatura) {
}