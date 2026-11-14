package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.CrearEventoInput;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.Fecha;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.ResultadoRecordatorios;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** eventos-service: eventos y agenda de fechas. */
@Component
public class EventosClient extends ClienteRemoto {
    private static final ParameterizedTypeReference<Pagina<Evento>> PAGINA = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Fecha>> FECHAS = new ParameterizedTypeReference<>() { };

    public EventosClient(@Qualifier("eventosRestClient") RestClient rest) {
        super(rest, "eventos");
    }

    /** GET /eventos/{id}; inexistente → NOT_FOUND. */
    public Evento obtener(String autorizacion, UUID id) {
        return ejecutar(rest.get().uri("/api/v1/eventos/{id}", id), autorizacion, Evento.class);
    }

    /** Fase 11H: igual, pero inexistente → {@code null} (campo {@code MiNotificacion.evento}). */
    public Evento obtenerOpcional(String autorizacion, UUID id) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}", id), autorizacion, Evento.class);
    }

    public Pagina<Evento> listar(String autorizacion, String estado, String tipo, LocalDate desde, LocalDate hasta,
                                 int page, int size, String sort) {
        return ejecutar(rest.get().uri(b -> b.path("/api/v1/eventos")
                .queryParamIfPresent("estado", Optional.ofNullable(estado))
                .queryParamIfPresent("tipo", Optional.ofNullable(tipo))
                .queryParamIfPresent("desde", Optional.ofNullable(desde))
                .queryParamIfPresent("hasta", Optional.ofNullable(hasta))
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .build()), autorizacion, PAGINA);
    }

    public List<Fecha> fechasDisponibles(String autorizacion, LocalDate desde, LocalDate hasta) {
        return ejecutar(rest.get().uri(b -> b.path("/api/v1/fechas/disponibles")
                .queryParamIfPresent("desde", Optional.ofNullable(desde))
                .queryParamIfPresent("hasta", Optional.ofNullable(hasta))
                .build()), autorizacion, FECHAS);
    }

    /** POST /eventos. */
    public Evento crear(String autorizacion, CrearEventoInput input) {
        return ejecutar(rest.post().uri("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).body(input),
                autorizacion, Evento.class);
    }

    /** PATCH /eventos/{id}/confirmar (409 sin contrato firmado o sin el depósito; 503 si Contratos o Finanzas no responden). */
    public Evento confirmar(String autorizacion, UUID id) {
        return ejecutar(rest.patch().uri("/api/v1/eventos/{id}/confirmar", id), autorizacion, Evento.class);
    }

    /** PATCH /eventos/{id}/cancelar. */
    public Evento cancelar(String autorizacion, UUID id) {
        return ejecutar(rest.patch().uri("/api/v1/eventos/{id}/cancelar", id), autorizacion, Evento.class);
    }

    /** Fase 11E: POST /recordatorios/ejecutar (solo ADMINISTRADOR; el servicio lo vuelve a comprobar con 403). */
    public ResultadoRecordatorios ejecutarRecordatorios(String autorizacion) {
        return ejecutar(rest.post().uri("/api/v1/recordatorios/ejecutar"), autorizacion, ResultadoRecordatorios.class);
    }

    /** PATCH /eventos/{id}/lugar. */
    public Evento asignarLugar(String autorizacion, UUID id, UUID lugarId) {
        return ejecutar(rest.patch().uri("/api/v1/eventos/{id}/lugar", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("lugarId", lugarId)), autorizacion, Evento.class);
    }
}
