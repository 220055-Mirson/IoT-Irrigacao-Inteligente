-- 1. TABELA DE CONFIGURACOES DO SISTEMA

CREATE TABLE IF NOT EXISTS system_config (
    id BIGSERIAL PRIMARY KEY,

    moisture_limit REAL NOT NULL DEFAULT 30,

    moisture_stop_limit REAL NOT NULL DEFAULT 45,

    temperature_limit REAL NOT NULL DEFAULT 20,

    operating_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_moisture_limit
        CHECK (moisture_limit >= 0 AND moisture_limit <= 100),

    CONSTRAINT check_moisture_stop
        CHECK (moisture_stop_limit >= 0 AND moisture_stop_limit <= 100),

    CONSTRAINT check_temperature
        CHECK (temperature_limit >= -40 AND temperature_limit <= 80),

    CONSTRAINT check_operating_mode
        CHECK (operating_mode IN ('AUTO', 'MANUAL'))
);


-- 2. TABELA DE LEITURAS DOS SENSORES

CREATE TABLE IF NOT EXISTS sensor_readings (
    id BIGSERIAL PRIMARY KEY,

    soil_moisture REAL NOT NULL,

    temperature REAL NOT NULL,

    air_humidity REAL NOT NULL,

    pump_on BOOLEAN NOT NULL DEFAULT FALSE,

    operating_mode VARCHAR(20) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_soil_moisture
        CHECK (soil_moisture >= 0 AND soil_moisture <= 100),

    CONSTRAINT check_air_humidity
        CHECK (air_humidity >= 0 AND air_humidity <= 100),

    CONSTRAINT check_reading_mode
        CHECK (operating_mode IN ('AUTO', 'MANUAL'))
);


-- 3. TABELA DE REGISTO DAS IRRIGACOES

CREATE TABLE IF NOT EXISTS irrigations (
    id BIGSERIAL PRIMARY KEY,

    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    end_time TIMESTAMP,

    reason VARCHAR(255) NOT NULL,

    mode VARCHAR(20) NOT NULL,

    CONSTRAINT check_irrigation_mode
        CHECK (mode IN ('AUTO', 'MANUAL')),

    CONSTRAINT check_irrigation_times
        CHECK (end_time IS NULL OR end_time >= start_time)
);


-- 4. INDICES PARA MELHORAR AS CONSULTAS

CREATE INDEX IF NOT EXISTS idx_readings_created_at
ON sensor_readings (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_irrigations_start_time
ON irrigations (start_time DESC);


-- 5. INSERIR CONFIGURACAO INICIAL

INSERT INTO system_config (
    moisture_limit,
    moisture_stop_limit,
    temperature_limit,
    operating_mode
)
SELECT 30, 45, 20, 'AUTO'
WHERE NOT EXISTS (
    SELECT 1 FROM system_config
);

