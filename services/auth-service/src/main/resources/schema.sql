-- Fase 11D: usuarios que pueden iniciar sesión (antes era una sola organizadora fija en variables de entorno).
-- Se siembran al arrancar desde el .env (SembradoUsuarios): el ADMINISTRADOR con AUTH_ORGANIZER_* y el ASISTENTE con
-- AUTH_ASISTENTE_*. La contraseña solo se guarda como hash BCrypt. El token emitido no se guarda (no hay logout ni
-- revocación). Lo ejecuta spring.sql.init antes de que Hibernate valide el esquema.
CREATE TABLE IF NOT EXISTS usuarios (
    id             UUID         PRIMARY KEY,
    correo         VARCHAR(150) NOT NULL UNIQUE,
    nombre         VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'ASISTENTE')),
    password_hash  VARCHAR(100) NOT NULL,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);
