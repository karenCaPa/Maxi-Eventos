package com.maxieventos.notificaciones.service;

import com.maxieventos.notificaciones.dto.MarcadasResponse;
import com.maxieventos.notificaciones.dto.MiNotificacionResponse;
import com.maxieventos.notificaciones.dto.PaginaResponse;
import com.maxieventos.notificaciones.dto.SinLeerResponse;
import com.maxieventos.notificaciones.entity.LecturaNotificacion;
import com.maxieventos.notificaciones.entity.Notificacion;
import com.maxieventos.notificaciones.exception.RecursoNoEncontradoException;
import com.maxieventos.notificaciones.repository.LecturaNotificacionRepository;
import com.maxieventos.notificaciones.repository.NotificacionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fase 11H: la bandeja de la campanita. Muestra las notificaciones de TODOS los eventos (la interfaz es interna: la
 * organizadora y el asistente ven las mismas) y, para cada una, si el usuario actual la leyó. Las lecturas son por usuario
 * ({@code usuario} = sub del JWT): lo que lee uno no cambia para el otro.
 */
@Service
public class LecturaNotificacionService {
    private final NotificacionRepository notificaciones;
    private final LecturaNotificacionRepository lecturas;

    public LecturaNotificacionService(NotificacionRepository notificaciones, LecturaNotificacionRepository lecturas) {
        this.notificaciones = notificaciones;
        this.lecturas = lecturas;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MiNotificacionResponse> recientes(String usuario, Pageable pageable) {
        Page<Notificacion> pagina = notificaciones.findAll(pageable);
        Map<UUID, Instant> leidas = pagina.isEmpty() ? Map.of()
                : lecturas.findByUsuarioAndNotificacionIdIn(usuario, pagina.map(Notificacion::getId).getContent()).stream()
                        .collect(Collectors.toMap(LecturaNotificacion::getNotificacionId, LecturaNotificacion::getLeidaEn));
        return PaginaResponse.de(pagina, n -> MiNotificacionResponse.de(n, leidas.get(n.getId())));
    }

    @Transactional(readOnly = true)
    public SinLeerResponse sinLeer(String usuario) {
        return new SinLeerResponse(lecturas.contarSinLeer(usuario));
    }

    /** Idempotente: si ya estaba leída, conserva la fecha de la primera lectura. Inexistente → 404. */
    @Transactional
    public MiNotificacionResponse marcarLeida(String usuario, UUID id) {
        Notificacion n = notificaciones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una notificación con id " + id));
        lecturas.marcar(id, usuario, Instant.now());
        Instant leidaEn = lecturas.findById(new LecturaNotificacion.Clave(id, usuario))
                .map(LecturaNotificacion::getLeidaEn).orElse(null);
        return MiNotificacionResponse.de(n, leidaEn);
    }

    @Transactional
    public MarcadasResponse marcarTodas(String usuario) {
        return new MarcadasResponse(lecturas.marcarTodas(usuario, Instant.now()));
    }
}
