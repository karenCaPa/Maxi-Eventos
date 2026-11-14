package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.AsignarProveedorInput;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.dto.ProveedorAsignado;
import com.maxieventos.gateway.dto.ProveedorInput;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** proveedores-service: proveedores y sus asignaciones a eventos. */
@Component
public class ProveedoresClient extends ClienteRemoto {
    private static final ParameterizedTypeReference<Pagina<ProveedorAsignado>> PAGINA = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Pagina<Proveedor>> PAGINA_PROVEEDORES = new ParameterizedTypeReference<>() { };

    public ProveedoresClient(@Qualifier("proveedoresRestClient") RestClient rest) {
        super(rest, "proveedores");
    }

    public Pagina<ProveedorAsignado> asignaciones(String autorizacion, UUID eventoId, int page, int size) {
        return ejecutarOpcional(rest.get().uri("/api/v1/proveedores/eventos/{id}?page={page}&size={size}", eventoId, page, size),
                autorizacion, PAGINA);
    }

    /** GET /proveedores/{id}; inexistente → {@code null}. */
    public Proveedor obtenerOpcional(String autorizacion, UUID id) {
        return ejecutarOpcional(rest.get().uri("/api/v1/proveedores/{id}", id), autorizacion, Proveedor.class);
    }

    public Pagina<Proveedor> listar(String autorizacion, String categoria, Boolean activo, int page, int size) {
        return ejecutar(rest.get().uri(b -> b.path("/api/v1/proveedores")
                .queryParamIfPresent("categoria", Optional.ofNullable(categoria))
                .queryParamIfPresent("activo", Optional.ofNullable(activo))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()), autorizacion, PAGINA_PROVEEDORES);
    }

    /** POST /proveedores (201; 400 por campo; 409 nombre repetido). */
    public Proveedor crear(String autorizacion, ProveedorInput input) {
        return ejecutar(rest.post().uri("/api/v1/proveedores").contentType(MediaType.APPLICATION_JSON).body(input),
                autorizacion, Proveedor.class);
    }

    /** PUT /proveedores/{id} (200; 400; 404; 409 nombre de otro proveedor). */
    public Proveedor actualizar(String autorizacion, UUID id, ProveedorInput input) {
        return ejecutar(rest.put().uri("/api/v1/proveedores/{id}", id).contentType(MediaType.APPLICATION_JSON).body(input),
                autorizacion, Proveedor.class);
    }

    /** PATCH /proveedores/{id}/estado con {"activo": true|false} (200; 404). */
    public Proveedor cambiarEstado(String autorizacion, UUID id, boolean activo) {
        return ejecutar(rest.patch().uri("/api/v1/proveedores/{id}/estado", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("activo", activo)), autorizacion, Proveedor.class);
    }

    /** POST /proveedores/eventos (404 evento, 409 proveedor inactivo o asignación duplicada, 503 si Eventos no responde). */
    public ProveedorAsignado asignar(String autorizacion, AsignarProveedorInput input) {
        return ejecutar(rest.post().uri("/api/v1/proveedores/eventos").contentType(MediaType.APPLICATION_JSON)
                .body(input.cuerpo()), autorizacion, ProveedorAsignado.class);
    }

    /** PATCH /proveedores/eventos/asignaciones/{id}/estado con {"estado": "CONFIRMADO"} (200; 404; 409 si no estaba PENDIENTE). */
    public ProveedorAsignado cambiarEstadoAsignacion(String autorizacion, UUID asignacionId, String estado) {
        return ejecutar(rest.patch().uri("/api/v1/proveedores/eventos/asignaciones/{id}/estado", asignacionId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("estado", estado)), autorizacion,
                ProveedorAsignado.class);
    }
}
