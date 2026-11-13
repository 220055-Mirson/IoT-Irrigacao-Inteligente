
package mz.grupo4.irrigacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mz.grupo4.irrigacao.model.Irrigation;

@Repository
public interface IrrigationRepository
        extends JpaRepository<Irrigation, Long> {

    // Consultar histórico de irrigações
    List<Irrigation> findAllByOrderByStartTimeDesc();

    // Encontrar uma irrigação que ainda não terminou
    Optional<Irrigation> findFirstByEndTimeIsNullOrderByStartTimeDesc();
}
