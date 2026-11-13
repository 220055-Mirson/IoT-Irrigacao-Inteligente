
package mz.grupo4.irrigacao.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "moisture_limit", nullable = false)
    private Float moistureLimit;

    @Column(name = "moisture_stop_limit", nullable = false)
    private Float moistureStopLimit;

    @Column(name = "temperature_limit", nullable = false)
    private Float temperatureLimit;

    @Column(name = "operating_mode", nullable = false)
    private String operatingMode;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }

    public SystemConfig() {
    }

    public Long getId() {
        return id;
    }

    public Float getMoistureLimit() {
        return moistureLimit;
    }

    public void setMoistureLimit(Float moistureLimit) {
        this.moistureLimit = moistureLimit;
    }

    public Float getMoistureStopLimit() {
        return moistureStopLimit;
    }

    public void setMoistureStopLimit(Float moistureStopLimit) {
        this.moistureStopLimit = moistureStopLimit;
    }

    public Float getTemperatureLimit() {
        return temperatureLimit;
    }

    public void setTemperatureLimit(Float temperatureLimit) {
        this.temperatureLimit = temperatureLimit;
    }

    public String getOperatingMode() {
        return operatingMode;
    }

    public void setOperatingMode(String operatingMode) {
        this.operatingMode = operatingMode;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
