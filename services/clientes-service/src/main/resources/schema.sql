CREATE TABLE IF NOT EXISTS clientes (
    id UUID PRIMARY KEY,
    nombre_completo VARCHAR(255),
    telefono VARCHAR(255),
    correo VARCHAR(255),
    direccion VARCHAR(255),
    CONSTRAINT uk_clientes_correo UNIQUE (correo),
    CONSTRAINT uk_clientes_telefono UNIQUE (telefono)
);
