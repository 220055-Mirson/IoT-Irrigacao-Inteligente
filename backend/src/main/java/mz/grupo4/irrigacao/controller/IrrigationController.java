package mz.grupo4.irrigacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mz.grupo4.irrigacao.model.Irrigation;
import mz.grupo4.irrigacao.service.IrrigationService;

// Registo das irrigações realizadas (data, hora, motivo e modo)
@RestController
@RequestMapping("/api/irrigations")
public class IrrigationController {

    private final IrrigationService service;

    public IrrigationController(IrrigationService service) {
        this.service = service;
    }

    // GET /api/irrigations?limit=50 -> histórico, da mais recente para a mais antiga
    @GetMapping
    public List<Irrigation> listar(@RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e 1000.");
        }
        return service.getLatestIrrigations(limit);
    }

    // GET /api/irrigations/active -> irrigação em curso (204 se não houver)
    @GetMapping("/active")
    public ResponseEntity<Irrigation> activa() {
        return service.getActiveIrrigation()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}