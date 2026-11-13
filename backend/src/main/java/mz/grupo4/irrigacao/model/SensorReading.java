
package mz.grupo4.irrigacao.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "sensor_readings")
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "soil_moisture", nullable = false)
    private Float soilMoisture;

    @Column(nullable = false)
    private Float temperature;

    @Column(name = "air_humidity", nullable = false)
    private Float airHumidity;

    @Column(name = "pump_on", nullable = false)
    private Boolean pumpOn = false;

    @Column(name = "operating_mode", nullable = false)
    private String operatingMode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public SensorReading() {
    }

    public Long getId() {
        return id;
    }

    public Float getSoilMoisture() {
        return soilMoisture;
    }

    public void setSoilMoisture(Float soilMoisture) {
        this.soilMoisture = soilMoisture;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Float getAirHumidity() {
        return airHumidity;
    }

    public void setAirHumidity(Float airHumidity) {
        this.airHumidity = airHumidity;
    }

    public Boolean getPumpOn() {
        return pumpOn;
    }

    public void setPumpOn(Boolean pumpOn) {
        this.pumpOn = pumpOn;
    }

    public String getOperatingMode() {
        return operatingMode;
    }

    public void setOperatingMode(String operatingMode) {
        this.operatingMode = operatingMode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
