package mz.grupo4.irrigacao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import mz.grupo4.irrigacao.dto.CommandMessage;
import mz.grupo4.irrigacao.dto.ConfigRequest;
import mz.grupo4.irrigacao.model.SystemConfig;
import mz.grupo4.irrigacao.service.MqttService;
import mz.grupo4.irrigacao.service.SystemConfigService;

// Configuração do sistema: limites de humidade e temperatura, e modo (AUTO / MANUAL)
@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    private final SystemConfigService service;
    private final MqttService mqtt;

    public SystemConfigController(SystemConfigService service, MqttService mqtt) {
        this.service = service;
        this.mqtt = mqtt;
    }

    // GET /api/config
    @GetMapping
    public SystemConfig obter() {
        return service.getConfiguration();
    }

    // PUT /api/config -> valida, guarda na base de dados e envia o comando ao ESP32
    @PutMapping
    public SystemConfig actualizar(@RequestBody ConfigRequest pedido) {
        SystemConfig config = service.updateConfiguration(
                pedido.moistureLimit(),
                pedido.moistureStopLimit(),
                pedido.temperatureLimit(),
                pedido.operatingMode());

        try {
            mqtt.publicarComando(new CommandMessage(
                    config.getOperatingMode(),
                    null,
                    config.getMoistureLimit(),
                    config.getMoistureStopLimit(),
                    config.getTemperatureLimit()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configuração guardada, mas o comando não chegou ao ESP32: " + e.getMessage());
        }
        return config;
    }
}