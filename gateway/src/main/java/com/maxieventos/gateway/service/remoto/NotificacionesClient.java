package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.MiNotificacion;
import com.maxieventos.gateway.dto.Notificacion;
import com.maxieventos.gateway.dto.Pagina;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** notificaciones-service. */
@Component
public class NotificacionesClient extends ClienteRemoto {
    private static final ParameterizedTypeReference<Pagina<Notificacion>> PAGINA = new ParameterizedTypeReference<>() { };

    public NotificacionesClient(@Qualifier("notificacionesRestClient") RestClient rest) {
        super(rest, "notificaciones");
    }

    public Pagina<Notificacion> deEvento(String autorizacion, UUID eventoId, int page, int size) {
        return ejecutar(rest.get().uri("/api/v1/notificaciones?eventoId={id}&page={page}&size={size}", eventoId, page, size),
                autorizacion, PAGINA);
    }

    // ---------------------------------------------------------------- Fase 11H: campanita (lecturas por usuario)

    private static final ParameterizedTypeReference<Pagina<MiNotificacion>> PAGINA_MIA = new ParameterizedTypeReference<>() { };

    record SinLeer(long sinLeer) { }

    record Marcadas(int marcadas) { }

    /** GET /notificaciones/recientes?page&size: todas, más recientes primero, con la lectura del usuario del token. */
    public Pagina<MiNotificacion> recientes(String autorizacion, int page, int size) {
        return ejecutar(rest.get().uri("/api/v1/notificaciones/recientes?page={page}&size={size}", page, size), autorizacion,
                PAGINA_MIA);
    }

    /** GET /notificaciones/sin-leer. */
    public long sinLeer(String autorizacion) {
        return ejecutar(rest.get().uri("/api/v1/notificaciones/sin-leer"), autorizacion, SinLeer.class).sinLeer();
    }

    /** PATCH /notificaciones/{id}/leida (idempotente; inexistente → NOT_FOUND). */
    public MiNotificacion marcarLeida(String autorizacion, UUID id) {
        return ejecutar(rest.patch().uri("/api/v1/notificaciones/{id}/leida", id), autorizacion, MiNotificacion.class);
    }

    /** PATCH /notificaciones/leidas: cuántas se marcaron ahora. */
    public int marcarTodas(String autorizacion) {
        return ejecutar(rest.patch().uri("/api/v1/notificaciones/leidas"), autorizacion, Marcadas.class).marcadas();
    }
}
