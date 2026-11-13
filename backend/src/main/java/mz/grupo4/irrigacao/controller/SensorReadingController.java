package mz.grupo4.irrigacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mz.grupo4.irrigacao.model.SensorReading;
import mz.grupo4.irrigacao.service.SensorReadingService;

// Consulta das leituras dos sensores (valores actuais e histórico)
@RestController
@RequestMapping("/api/readings")
public class SensorReadingController {

    private final SensorReadingService service;

    public SensorReadingController(SensorReadingService service) {
        this.service = service;
    }

    // GET /api/readings?limit=100 -> histórico, da mais recente para a mais antiga
    @GetMapping
    public List<SensorReading> listar(@RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e 1000.");
        }
        return service.getLatestReadings(limit);
    }

    // GET /api/readings/latest -> valores actuais (204 se ainda não há leituras)
    @GetMapping("/latest")
    public ResponseEntity<SensorReading> ultima() {
        return service.getLatestReading()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}