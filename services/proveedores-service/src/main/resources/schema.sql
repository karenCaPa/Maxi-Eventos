CREATE TABLE IF NOT EXISTS proveedores (
    id UUID PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    categoria VARCHAR(255) NOT NULL,
    contacto VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS evento_proveedores (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    proveedor_id UUID NOT NULL,
    servicio_asignado VARCHAR(255) NOT NULL,
    costo_acordado NUMERIC(19, 2) NOT NULL,
    estado VARCHAR(255) NOT NULL, -- PENDIENTE | CONFIRMADO | CANCELADO (enum EstadoAsignacion)
    CONSTRAINT uk_evento_proveedor_servicio UNIQUE (evento_id, proveedor_id, servicio_asignado),
    CONSTRAINT fk_evento_proveedor_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
);
