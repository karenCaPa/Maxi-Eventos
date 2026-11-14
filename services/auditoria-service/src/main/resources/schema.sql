-- Fase 5C: un registro por cada mensaje Kafka válido recibido de los tres tópicos (auditoría). payload = JSON original.
CREATE TABLE IF NOT EXISTS auditoria_eventos (
    id_evento UUID PRIMARY KEY,
    tipo VARCHAR(40) NOT NULL,
    evento_id UUID NOT NULL,
    servicio_origen VARCHAR(100),
    payload TEXT NOT NULL,
    topico VARCHAR(100) NOT NULL,
    particion INTEGER NOT NULL,
    offset_kafka BIGINT NOT NULL,
    fecha_ocurrencia TIMESTAMP WITH TIME ZONE,
    fecha_recepcion TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auditoria_eventos_evento ON auditoria_eventos (evento_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_eventos_tipo ON auditoria_eventos (tipo);

-- Fase 5C: idempotencia del consumidor Kafka de este servicio (un mensaje se procesa una vez por grupo).
CREATE TABLE IF NOT EXISTS auditoria_eventos_procesados (
    id_evento UUID NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    evento_id UUID,
    topico VARCHAR(100) NOT NULL,
    particion INTEGER NOT NULL,
    offset_kafka BIGINT NOT NULL,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_auditoria_eventos_procesados PRIMARY KEY (id_evento, consumer_group)
);
