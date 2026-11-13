
package mz.grupo4.irrigacao.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mz.grupo4.irrigacao.model.SystemConfig;

@Repository
public interface SystemConfigRepository
        extends JpaRepository<SystemConfig, Long> {

    // Obter a primeira configuração registada
    Optional<SystemConfig> findFirstByOrderByIdAsc();
}
