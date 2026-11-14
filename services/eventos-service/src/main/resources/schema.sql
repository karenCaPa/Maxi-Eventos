-- Tablas de eventos-service (Fase 3). La tabla antigua del simulador se renombró a eventos_simulador_fase2
-- con docker/postgres/fase3-renombrar-eventos.sql.
CREATE TABLE IF NOT EXISTS eventos (
    id UUID PRIMARY KEY,
    nombre_evento VARCHAR(150) NOT NULL,
    fecha DATE NOT NULL,
    cliente_id UUID NOT NULL,
    tipo_evento VARCHAR(20) NOT NULL,
    cantidad_adultos INTEGER NOT NULL,
    cantidad_ninos INTEGER NOT NULL,
    num_invitados INTEGER NOT NULL,
    zona VARCHAR(20) NOT NULL,
    lugar_id UUID,
    estado VARCHAR(20) NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_eventos_fecha ON eventos (fecha);
CREATE INDEX IF NOT EXISTS idx_eventos_estado ON eventos (estado);

CREATE TABLE IF NOT EXISTS fechas_evento (
    fecha DATE PRIMARY KEY,
    estado VARCHAR(255) NOT NULL,
    evento_id UUID
);

-- Fase 5A2: outbox de los mensajes Kafka (id = idEvento del sobre). Las filas PUBLICADO se conservan como registro.
CREATE TABLE IF NOT EXISTS eventos_mensajes_salientes (
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

CREATE INDEX IF NOT EXISTS idx_eventos_mensajes_salientes_estado ON eventos_mensajes_salientes (estado, actualizado_en);

-- Fase 11E: recordatorios EVENTO_PROXIMO ya enviados (a lo sumo uno por evento y umbral). Se guarda en la misma
-- transacción que la fila del outbox. id_evento = idEvento determinista del mensaje.
CREATE TABLE IF NOT EXISTS eventos_recordatorios_enviados (
    id_evento UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    umbral_dias INTEGER NOT NULL,
    dias_restantes INTEGER NOT NULL,
    fecha_evento DATE NOT NULL,
    enviado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_recordatorio_evento_umbral UNIQUE (evento_id, umbral_dias)
);
