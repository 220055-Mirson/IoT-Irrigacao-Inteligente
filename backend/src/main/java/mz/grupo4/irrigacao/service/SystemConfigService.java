
package mz.grupo4.irrigacao.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mz.grupo4.irrigacao.model.SystemConfig;
import mz.grupo4.irrigacao.repository.SystemConfigRepository;

@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    // Consultar configurações
    public SystemConfig getConfiguration() {

        return repository.findFirstByOrderByIdAsc()
            .orElseThrow(() -> new IllegalStateException(
                "Configuração do sistema não encontrada."
            ));
    }

    // Actualizar configurações
    @Transactional
    public SystemConfig updateConfiguration(
            Float moistureLimit,
            Float moistureStopLimit,
            Float temperatureLimit,
            String operatingMode) {

        if (moistureLimit == null ||
            moistureStopLimit == null ||
            temperatureLimit == null) {

            throw new IllegalArgumentException(
                "Todos os limites são obrigatórios."
            );
        }

        if (moistureLimit < 0 || moistureLimit > 100 ||
            moistureStopLimit < 0 || moistureStopLimit > 100) {

            throw new IllegalArgumentException(
                "Os limites de humidade devem estar entre 0 e 100."
            );
        }

        if (moistureStopLimit <= moistureLimit) {
            throw new IllegalArgumentException(
                "O limite de paragem deve ser superior ao limite de activação."
            );
        }

        if (temperatureLimit < -40 || temperatureLimit > 80) {
            throw new IllegalArgumentException(
                "Temperatura configurada inválida."
            );
        }

        if (!"AUTO".equals(operatingMode) &&
            !"MANUAL".equals(operatingMode)) {

            throw new IllegalArgumentException(
                "Modo de funcionamento inválido."
            );
        }

        SystemConfig config = getConfiguration();

        config.setMoistureLimit(moistureLimit);
        config.setMoistureStopLimit(moistureStopLimit);
        config.setTemperatureLimit(temperatureLimit);
        config.setOperatingMode(operatingMode);

        return repository.save(config);
    }
}
