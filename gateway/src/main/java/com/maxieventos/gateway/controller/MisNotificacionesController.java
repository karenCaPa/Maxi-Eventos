package com.maxieventos.gateway.controller;

import static com.maxieventos.gateway.config.TokenInterceptor.AUTORIZACION;

import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.MiNotificacion;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.service.remoto.EventosClient;
import com.maxieventos.gateway.service.remoto.NotificacionesClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * Fase 11H: la campanita. Cada operación delega en una sola llamada REST a notificaciones-service con el token tal cual
 * (el servicio saca de él el usuario: las lecturas son por usuario). Los dos roles la usan. {@code MiNotificacion.evento}
 * solo se resuelve si la query lo pide (la interfaz lo pide al abrir el panel, no en el contador) y con {@link BatchMapping}:
 * una llamada a Eventos por evento DISTINTO de la página, no una por notificación.
 */
@Controller
public class MisNotificacionesController {
    private final NotificacionesClient notificaciones;
    private final EventosClient eventos;

    public MisNotificacionesController(NotificacionesClient notificaciones, EventosClient eventos) {
        this.notificaciones = notificaciones;
        this.eventos = eventos;
    }

    @QueryMapping
    public Pagina<MiNotificacion> misNotificaciones(@Argument int page, @Argument int size,
                                                    @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return notificaciones.recientes(autorizacion, page, size);
    }

    @QueryMapping
    public long notificacionesSinLeer(@ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return notificaciones.sinLeer(autorizacion);
    }

    @MutationMapping
    public MiNotificacion marcarNotificacionLeida(@Argument UUID id,
                                                 @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return notificaciones.marcarLeida(autorizacion, id);
    }

    @MutationMapping
    public int marcarTodasNotificacionesLeidas(@ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return notificaciones.marcarTodas(autorizacion);
    }

    @BatchMapping(typeName = "MiNotificacion")
    public Map<MiNotificacion, Evento> evento(List<MiNotificacion> lista,
                                              @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        Map<UUID, Evento> porId = new LinkedHashMap<>();
        lista.stream().map(MiNotificacion::eventoId).filter(Objects::nonNull).distinct()
                .forEach(id -> porId.put(id, eventos.obtenerOpcional(autorizacion, id)));
        Map<MiNotificacion, Evento> resultado = new LinkedHashMap<>();
        for (MiNotificacion n : lista) {
            Evento e = n.eventoId() == null ? null : porId.get(n.eventoId());
            if (e != null) {
                resultado.put(n, e);
            }
        }
        return resultado;
    }
}
