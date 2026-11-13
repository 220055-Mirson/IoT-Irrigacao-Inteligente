package mz.grupo4.irrigacao.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mz.grupo4.irrigacao.model.Irrigation;
import mz.grupo4.irrigacao.repository.IrrigationRepository;

@Service
public class IrrigationService {

    private final IrrigationRepository repository;

    public IrrigationService(IrrigationRepository repository) {
        this.repository = repository;
    }

    // Registar o início de uma irrigação
    @Transactional
    public Irrigation startIrrigation(String reason, String mode) {

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                "O motivo da irrigação é obrigatório."
            );
        }

        if (!"AUTO".equals(mode) && !"MANUAL".equals(mode)) {
            throw new IllegalArgumentException(
                "Modo de irrigação inválido."
            );
        }

        if (repository.findFirstByEndTimeIsNullOrderByStartTimeDesc()
                .isPresent()) {

            throw new IllegalStateException(
                "Já existe uma irrigação em execução."
            );
        }

        Irrigation irrigation = new Irrigation();

        irrigation.setStartTime(LocalDateTime.now());
        irrigation.setReason(reason);
        irrigation.setMode(mode);

        return repository.save(irrigation);
    }

    // Registar o fim da irrigação actual
    @Transactional
    public Irrigation finishIrrigation() {

        Irrigation irrigation = repository
            .findFirstByEndTimeIsNullOrderByStartTimeDesc()
            .orElseThrow(() -> new IllegalStateException(
                "Não existe irrigação em execução."
            ));

        irrigation.setEndTime(LocalDateTime.now());

        return repository.save(irrigation);
    }

    // Consultar histórico
    public List<Irrigation> getAllIrrigations() {
        return repository.findAllByOrderByStartTimeDesc();
    }

    // Consultar as N irrigações mais recentes
    public List<Irrigation> getLatestIrrigations(int limit) {
        return repository.findAll(
            PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startTime"))
        ).getContent();
    }

    // Verificar se existe irrigação activa
    public Optional<Irrigation> getActiveIrrigation() {
        return repository
            .findFirstByEndTimeIsNullOrderByStartTimeDesc();
    }
}