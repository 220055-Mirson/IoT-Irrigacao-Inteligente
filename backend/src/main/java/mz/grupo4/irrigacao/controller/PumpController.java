package mz.grupo4.irrigacao.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import mz.grupo4.irrigacao.dto.CommandMessage;
import mz.grupo4.irrigacao.dto.PumpRequest;
import mz.grupo4.irrigacao.model.SystemConfig;
import mz.grupo4.irrigacao.service.MqttService;
import mz.grupo4.irrigacao.service.SystemConfigService;

// Comando manual da bomba (só funciona no modo MANUAL)
@RestController
@RequestMapping("/api/pump")
public class PumpController {

    private final SystemConfigService configService;
    private final MqttService mqtt;

    public PumpController(SystemConfigService configService, MqttService mqtt) {
        this.configService = configService;
        this.mqtt = mqtt;
    }

    // POST /api/pump  {"pumpOn": true}
    // O registo da irrigação é feito quando o ESP32 envia o evento INICIO / FIM.
    @PostMapping
    public Map<String, String> accionar(@RequestBody PumpRequest pedido) {
        if (pedido.pumpOn() == null) {
            throw new IllegalArgumentException("O campo pumpOn é obrigatório.");
        }

        SystemConfig config = configService.getConfiguration();
        if (!"MANUAL".equals(config.getOperatingMode())) {
            throw new IllegalStateException("A bomba só pode ser comandada no modo manual.");
        }

        try {
            mqtt.publicarComando(new CommandMessage("MANUAL", pedido.pumpOn(), null, null, null));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O comando não chegou ao ESP32: " + e.getMessage());
        }

        return Map.of("mensagem", pedido.pumpOn()
                ? "Comando para ligar a bomba enviado."
                : "Comando para desligar a bomba enviado.");
    }
}