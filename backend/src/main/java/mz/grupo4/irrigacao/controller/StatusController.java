package mz.grupo4.irrigacao.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mz.grupo4.irrigacao.dto.StatusResponse;
import mz.grupo4.irrigacao.model.SensorReading;
import mz.grupo4.irrigacao.service.IrrigationService;
import mz.grupo4.irrigacao.service.MqttService;
import mz.grupo4.irrigacao.service.SensorReadingService;
import mz.grupo4.irrigacao.service.SystemConfigService;

// Estado geral do sistema (para o dashboard)
@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final SystemConfigService configService;
    private final SensorReadingService readingService;
    private final IrrigationService irrigationService;
    private final MqttService mqtt;

    public StatusController(SystemConfigService configService,
                            SensorReadingService readingService,
                            IrrigationService irrigationService,
                            MqttService mqtt) {
        this.configService = configService;
        this.readingService = readingService;
        this.irrigationService = irrigationService;
        this.mqtt = mqtt;
    }

    // GET /api/status
    @GetMapping
    public StatusResponse estado() {
        Boolean bombaLigada = readingService.getLatestReading()
                .map(SensorReading::getPumpOn)
                .orElse(null);

        return new StatusResponse(
                mqtt.getEstadoEsp32(),
                configService.getConfiguration().getOperatingMode(),
                bombaLigada,
                irrigationService.getActiveIrrigation().isPresent());
    }
}