CREATE TABLE IF NOT EXISTS contratos (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    nombre_evento VARCHAR(255) NOT NULL,
    estado VARCHAR(255) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_contrato_evento UNIQUE (evento_id)
);

-- Fase 4: el estado inicial se llama PENDIENTE (antes BORRADOR). Idempotente: no hace nada si ya no quedan filas BORRADOR.
UPDATE contratos SET estado = 'PENDIENTE' WHERE estado = 'BORRADOR';

-- Fase 4: fecha de firma y foto de los datos del evento al crear el contrato (nulas en los contratos anteriores).
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS firmado_en TIMESTAMP WITH TIME ZONE;
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS cliente_nombre VARCHAR(255);
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS cliente_telefono VARCHAR(255);
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS cliente_correo VARCHAR(255);
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS fecha_evento DATE;
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS tipo_evento VARCHAR(20);
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS cantidad_adultos INTEGER;
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS cantidad_ninos INTEGER;
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS lugar_nombre VARCHAR(255);
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS lugar_direccion VARCHAR(255);
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS valor_total NUMERIC(19, 2);
ALTER TABLE contratos ADD COLUMN IF NOT EXISTS moneda VARCHAR(10);

-- Fase 5B: servicios adicionales recibidos por Kafka (SERVICIO_ADICIONAL_AGREGADO); fuente del anexo. Se guardan por
-- evento aunque el contrato todavía no exista.
CREATE TABLE IF NOT EXISTS contrato_adicionales (
    adicional_id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    codigo VARCHAR(100) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    cantidad INTEGER NOT NULL,
    valor_unitario NUMERIC(19, 2) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    moneda VARCHAR(10),
    recibido_en TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_contrato_adicionales_evento ON contrato_adicionales (evento_id);

-- Fase 5B: idempotencia del consumidor Kafka de este servicio (un mensaje se procesa una vez por grupo).
CREATE TABLE IF NOT EXISTS contratos_eventos_procesados (
    id_evento UUID NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    evento_id UUID,
    topico VARCHAR(100) NOT NULL,
    particion INTEGER NOT NULL,
    offset_kafka BIGINT NOT NULL,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_contratos_eventos_procesados PRIMARY KEY (id_evento, consumer_group)
);
