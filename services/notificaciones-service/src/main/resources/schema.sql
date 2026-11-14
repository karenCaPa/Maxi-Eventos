-- Fase 5C: notificaciones simuladas (no se envía ningún correo). Una por mensaje Kafka (id_evento único).
CREATE TABLE IF NOT EXISTS notificaciones (
    id UUID PRIMARY KEY,
    id_evento UUID NOT NULL,
    evento_id UUID NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    destinatario VARCHAR(100) NOT NULL,
    asunto VARCHAR(255) NOT NULL,
    mensaje TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_notificaciones_id_evento UNIQUE (id_evento)
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_evento ON notificaciones (evento_id);
CREATE INDEX IF NOT EXISTS idx_notificaciones_fecha ON notificaciones (fecha_creacion DESC);

-- Fase 11H: qué notificaciones leyó cada usuario de la interfaz (campanita). "leída" es POR USUARIO: usuario = sub del JWT
-- (id opaco de la organizadora o del asistente, nunca el correo). Una fila por (notificación, usuario); sin fila = no leída.
CREATE TABLE IF NOT EXISTS notificaciones_lecturas (
    notificacion_id UUID NOT NULL REFERENCES notificaciones (id) ON DELETE CASCADE,
    usuario VARCHAR(100) NOT NULL,
    leida_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notificaciones_lecturas PRIMARY KEY (notificacion_id, usuario)
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_lecturas_usuario ON notificaciones_lecturas (usuario);

-- Fase 5C: idempotencia del consumidor Kafka de este servicio (un mensaje se procesa una vez por grupo).
CREATE TABLE IF NOT EXISTS notificaciones_eventos_procesados (
    id_evento UUID NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    evento_id UUID,
    topico VARCHAR(100) NOT NULL,
    particion INTEGER NOT NULL,
    offset_kafka BIGINT NOT NULL,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notificaciones_eventos_procesados PRIMARY KEY (id_evento, consumer_group)
);
