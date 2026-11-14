CREATE TABLE IF NOT EXISTS pagos (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    metodo_pago VARCHAR(255) NOT NULL,
    monto NUMERIC(19, 2) NOT NULL,
    moneda VARCHAR(255) NOT NULL,
    estado VARCHAR(255) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS cotizaciones (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL UNIQUE,
    invitados INTEGER NOT NULL,
    zona VARCHAR(255) NOT NULL,
    valor_base NUMERIC(19, 2) NOT NULL,
    recargo_zona NUMERIC(19, 2) NOT NULL,
    total NUMERIC(19, 2) NOT NULL,
    monto_reserva_requerido NUMERIC(19, 2),
    moneda VARCHAR(255) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_cotizacion_evento UNIQUE (evento_id)
);

-- Fase 4: idempotencia de pagos en PostgreSQL (antes en Redis). Los pagos anteriores quedan con la clave nula
-- (PostgreSQL admite varios NULL en un índice UNIQUE).
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS clave_idempotencia VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pago_clave_idempotencia ON pagos (clave_idempotencia);
CREATE INDEX IF NOT EXISTS idx_pagos_evento ON pagos (evento_id);

-- Fase 4: desglose de la cotización (nulo en las cotizaciones anteriores).
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS tipo_evento VARCHAR(20);
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS cantidad_adultos INTEGER;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS cantidad_ninos INTEGER;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS adultos_adicionales INTEGER;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS ninos_adicionales INTEGER;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS valor_adultos_adicionales NUMERIC(19, 2);
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS valor_ninos_adicionales NUMERIC(19, 2);
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS subtotal NUMERIC(19, 2);

CREATE TABLE IF NOT EXISTS adicionales_evento (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    codigo VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    cantidad INTEGER NOT NULL,
    valor_unitario NUMERIC(19, 2) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Fase 5A2: outbox de los mensajes Kafka (id = idEvento del sobre). Las filas PUBLICADO se conservan como registro.
CREATE TABLE IF NOT EXISTS finanzas_mensajes_salientes (
    id_evento UUID PRIMARY KEY,
    tipo VARCHAR(40) NOT NULL,
    topico VARCHAR(100) NOT NULL,
    clave VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    intentos INTEGER NOT NULL DEFAULT 0,
    ultimo_error VARCHAR(500),
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    tomado_en TIMESTAMP WITH TIME ZONE,
    publicado_en TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_finanzas_mensajes_salientes_estado ON finanzas_mensajes_salientes (estado, actualizado_en);
