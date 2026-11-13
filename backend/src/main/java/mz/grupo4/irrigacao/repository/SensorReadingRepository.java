
package mz.grupo4.irrigacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mz.grupo4.irrigacao.model.SensorReading;

@Repository
public interface SensorReadingRepository
        extends JpaRepository<SensorReading, Long> {

    // Obter a leitura mais recente
    Optional<SensorReading> findTopByOrderByCreatedAtDesc();

    // Obter todas as leituras por ordem decrescente
    List<SensorReading> findAllByOrderByCreatedAtDesc();
}
