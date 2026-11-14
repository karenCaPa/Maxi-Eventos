package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.LugarInput;
import com.maxieventos.gateway.dto.Pagina;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** lugares-service. */
@Component
public class LugaresClient extends ClienteRemoto {
    private static final ParameterizedTypeReference<Pagina<Lugar>> PAGINA = new ParameterizedTypeReference<>() { };

    public LugaresClient(@Qualifier("lugaresRestClient") RestClient rest) {
        super(rest, "lugares");
    }

    /** GET /lugares/{id}; inexistente → {@code null}. */
    public Lugar obtenerOpcional(String autorizacion, UUID id) {
        return ejecutarOpcional(rest.get().uri("/api/v1/lugares/{id}", id), autorizacion, Lugar.class);
    }

    public Pagina<Lugar> listar(String autorizacion, String nombre, String tipo, String estado, int page, int size) {
        return ejecutar(rest.get().uri(b -> b.path("/api/v1/lugares")
                .queryParamIfPresent("nombre", Optional.ofNullable(nombre))
                .queryParamIfPresent("tipo", Optional.ofNullable(tipo))
                .queryParamIfPresent("estado", Optional.ofNullable(estado))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()), autorizacion, PAGINA);
    }

    /** POST /lugares (201; 400 con errores por campo). */
    public Lugar crear(String autorizacion, LugarInput input) {
        return ejecutar(rest.post().uri("/api/v1/lugares").contentType(MediaType.APPLICATION_JSON).body(input),
                autorizacion, Lugar.class);
    }

    /** PUT /lugares/{id} (200; 400; 404). */
    public Lugar actualizar(String autorizacion, UUID id, LugarInput input) {
        return ejecutar(rest.put().uri("/api/v1/lugares/{id}", id).contentType(MediaType.APPLICATION_JSON).body(input),
                autorizacion, Lugar.class);
    }

    /** PATCH /lugares/{id}/estado con {"estado": ACTIVO|INACTIVO} (200; 404). */
    public Lugar cambiarEstado(String autorizacion, UUID id, String estado) {
        return ejecutar(rest.patch().uri("/api/v1/lugares/{id}/estado", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("estado", estado)), autorizacion, Lugar.class);
    }
}
