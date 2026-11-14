CREATE TABLE IF NOT EXISTS logistica_eventos (
    evento_id UUID PRIMARY KEY,
    meseros_requeridos INTEGER,
    personal_cocina_requerido INTEGER,
    camiones_transporte INTEGER
);

CREATE TABLE IF NOT EXISTS items_compra (
    id UUID PRIMARY KEY,
    nombre_item VARCHAR(255),
    cantidad_estimada VARCHAR(255),
    comprado BOOLEAN NOT NULL DEFAULT FALSE,
    evento_id UUID NOT NULL REFERENCES logistica_eventos(evento_id) ON DELETE CASCADE,
    origen VARCHAR(20) NOT NULL DEFAULT 'CALCULADO',
    nota VARCHAR(255)
);

-- 10B3: origen del ítem (CALCULADO por el sistema o MANUAL) y nota opcional. En una base existente las filas que ya
-- estaban quedan como CALCULADO (las generó el consumidor de EVENTO_CONFIRMADO). Se ejecuta antes que Hibernate.
ALTER TABLE items_compra ADD COLUMN IF NOT EXISTS origen VARCHAR(20) NOT NULL DEFAULT 'CALCULADO';
ALTER TABLE items_compra ADD COLUMN IF NOT EXISTS nota VARCHAR(255);

-- Desde la Fase 5B ya NO se usa (la reemplaza logistica_eventos_procesados). Se conserva con sus datos.
CREATE TABLE IF NOT EXISTS processed_events (
    event_idempotencia UUID PRIMARY KEY,
    evento_id UUID NOT NULL UNIQUE,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Fase 5B: idempotencia de los consumidores Kafka de este servicio (un mensaje se procesa una vez por grupo).
CREATE TABLE IF NOT EXISTS logistica_eventos_procesados (
    id_evento UUID NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    evento_id UUID,
    topico VARCHAR(100) NOT NULL,
    particion INTEGER NOT NULL,
    offset_kafka BIGINT NOT NULL,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_logistica_eventos_procesados PRIMARY KEY (id_evento, consumer_group)
);

-- Fase 5B: servicios adicionales recibidos y su efecto en la logística (se aplican al confirmar si llegan antes).
CREATE TABLE IF NOT EXISTS logistica_adicionales (
    adicional_id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    codigo VARCHAR(100) NOT NULL,
    cantidad INTEGER NOT NULL,
    meseros_extra INTEGER NOT NULL,
    cocina_extra INTEGER NOT NULL,
    camiones_extra INTEGER NOT NULL,
    aplicado BOOLEAN NOT NULL,
    recibido_en TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_logistica_adicionales_evento ON logistica_adicionales (evento_id);
