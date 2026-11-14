package com.maxieventos.notificaciones.repository;

import com.maxieventos.notificaciones.entity.LecturaNotificacion;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Fase 11H: lecturas de notificaciones por usuario (campanita). */
public interface LecturaNotificacionRepository extends JpaRepository<LecturaNotificacion, LecturaNotificacion.Clave> {

    List<LecturaNotificacion> findByUsuarioAndNotificacionIdIn(String usuario, Collection<UUID> notificaciones);

    /** Notificaciones que el usuario todavía no leyó (de todos los eventos). */
    @Query(value = "SELECT count(*) FROM notificaciones n WHERE NOT EXISTS (SELECT 1 FROM notificaciones_lecturas l "
            + "WHERE l.notificacion_id = n.id AND l.usuario = :usuario)", nativeQuery = true)
    long contarSinLeer(@Param("usuario") String usuario);

    /** Marca una como leída; si ya lo estaba, no hace nada (0 filas). */
    @Modifying
    @Query(value = "INSERT INTO notificaciones_lecturas (notificacion_id, usuario, leida_en) VALUES (:id, :usuario, :ahora) "
            + "ON CONFLICT (notificacion_id, usuario) DO NOTHING", nativeQuery = true)
    int marcar(@Param("id") UUID id, @Param("usuario") String usuario, @Param("ahora") Instant ahora);

    /** Marca como leídas todas las que el usuario aún no había leído; devuelve cuántas marcó. */
    @Modifying
    @Query(value = "INSERT INTO notificaciones_lecturas (notificacion_id, usuario, leida_en) SELECT n.id, :usuario, :ahora "
            + "FROM notificaciones n ON CONFLICT (notificacion_id, usuario) DO NOTHING", nativeQuery = true)
    int marcarTodas(@Param("usuario") String usuario, @Param("ahora") Instant ahora);
}
