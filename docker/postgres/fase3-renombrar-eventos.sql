-- ============================================================================
-- Fase 3 — eventos-service completo
-- La tabla `eventos` del simulador (Fase 2) tiene un esquema incompatible con la entidad `Evento` nueva
-- (columnas obligatorias event_idempotencia, nombre_evento... y sin fecha, cliente_id ni estado).
-- Para no perder los datos de prueba se RENOMBRA (no se borra). eventos-service crea la tabla nueva al arrancar.
--
-- Es idempotente: si ya se ejecutó (o la tabla ya tiene el esquema nuevo), no hace nada.
-- Ejecución (desde la raíz del proyecto, con postgres-db levantado):
--   Get-Content docker\postgres\fase3-renombrar-eventos.sql | docker exec -i postgres_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
-- ============================================================================
DO $$
DECLARE
    restriccion RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'eventos' AND column_name = 'event_idempotencia') THEN
        RAISE NOTICE 'Nada que hacer: la tabla eventos no tiene el esquema del simulador.';
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'eventos_simulador_fase2') THEN
        RAISE EXCEPTION 'Ya existe eventos_simulador_fase2; revisar manualmente antes de continuar.';
    END IF;

    ALTER TABLE eventos RENAME TO eventos_simulador_fase2;

    -- Las restricciones (y sus índices) conservan el nombre "eventos_..."; se renombran para que la tabla nueva
    -- pueda crear eventos_pkey sin conflicto.
    FOR restriccion IN
        SELECT conname FROM pg_constraint WHERE conrelid = 'public.eventos_simulador_fase2'::regclass
    LOOP
        EXECUTE format('ALTER TABLE eventos_simulador_fase2 RENAME CONSTRAINT %I TO %I',
                       restriccion.conname, 'sim_fase2_' || restriccion.conname);
    END LOOP;

    RAISE NOTICE 'Tabla eventos renombrada a eventos_simulador_fase2 (% filas conservadas).',
        (SELECT count(*) FROM eventos_simulador_fase2);
END $$;
