CREATE TABLE IF NOT EXISTS lugares (
    id UUID PRIMARY KEY,
    nombre VARCHAR(255),
    direccion VARCHAR(255),
    tipo VARCHAR(255),
    capacidad INTEGER
);

-- Fase 4: estado del lugar. Los lugares existentes quedan ACTIVO.
ALTER TABLE lugares ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO';
