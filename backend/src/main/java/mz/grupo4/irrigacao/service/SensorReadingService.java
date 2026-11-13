package mz.grupo4.irrigacao.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import mz.grupo4.irrigacao.model.SensorReading;
import mz.grupo4.irrigacao.repository.SensorReadingRepository;

@Service
public class SensorReadingService {

    private final SensorReadingRepository repository;

    public SensorReadingService(SensorReadingRepository repository) {
        this.repository = repository;
    }

    // Guardar uma nova leitura
    public SensorReading saveReading(SensorReading reading) {

        if (reading.getSoilMoisture() == null ||
            reading.getTemperature() == null ||
            reading.getAirHumidity() == null ||
            reading.getPumpOn() == null) {

            throw new IllegalArgumentException(
                "Os valores dos sensores são obrigatórios."
            );
        }

        if (reading.getSoilMoisture() < 0 ||
            reading.getSoilMoisture() > 100) {

            throw new IllegalArgumentException(
                "Humidade do solo inválida."
            );
        }

        if (reading.getAirHumidity() < 0 ||
            reading.getAirHumidity() > 100) {

            throw new IllegalArgumentException(
                "Humidade ambiental inválida."
            );
        }

        if (reading.getTemperature() < -40 ||
            reading.getTemperature() > 80) {

            throw new IllegalArgumentException(
                "Temperatura inválida."
            );
        }

        if (!"AUTO".equals(reading.getOperatingMode()) &&
            !"MANUAL".equals(reading.getOperatingMode())) {

            throw new IllegalArgumentException(
                "Modo de funcionamento inválido."
            );
        }

        return repository.save(reading);
    }

    // Obter todas as leituras
    public List<SensorReading> getAllReadings() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    // Obter as N leituras mais recentes (evita carregar a tabela inteira)
    public List<SensorReading> getLatestReadings(int limit) {
        return repository.findAll(
            PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
    }

    // Obter a leitura mais recente
    public Optional<SensorReading> getLatestReading() {
        return repository.findTopByOrderByCreatedAtDesc();
    }

    // Procurar leitura pelo ID
    public Optional<SensorReading> getReadingById(Long id) {
        return repository.findById(id);
    }
}