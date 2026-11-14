package com.maxieventos.auth.entity;

/**
 * Roles de la plataforma (Fase 11D). Viajan en el claim {@code rol} del JWT (no es un dato sensible) y cada servicio y el
 * gateway deciden con él qué se permite. La matriz completa está en {@code docs/seguridad.md}.
 */
public enum Rol {
    /** Todos los permisos (la organizadora). */
    ADMINISTRADOR,
    /** Gestiona clientes y el flujo de los eventos; no administra lugares, proveedores, el bloqueo de fechas ni la auditoría. */
    ASISTENTE
}
