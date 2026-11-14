package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.ClienteInput;
import com.maxieventos.gateway.dto.Pagina;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** clientes-service. */
@Component
public class ClientesClient extends ClienteRemoto {
    private static final ParameterizedTypeReference<Pagina<Cliente>> PAGINA = new ParameterizedTypeReference<>() { };

    public ClientesClient(@Qualifier("clientesRestClient") RestClient rest) {
        super(rest, "clientes");
    }

    /** GET /clientes/{id}; inexistente → NOT_FOUND (query {@code cliente}). */
    public Cliente obtener(String autorizacion, UUID id) {
        return ejecutar(rest.get().uri("/api/v1/clientes/{id}", id), autorizacion, Cliente.class);
    }

    /** Igual, pero inexistente → {@code null} (campo {@code Evento.cliente}). */
    public Cliente obtenerOpcional(String autorizacion, UUID id) {
        return ejecutarOpcional(rest.get().uri("/api/v1/clientes/{id}", id), autorizacion, Cliente.class);
    }

    /** GET /clientes?nombre&page&size&sort (directorio para elegir el cliente de un evento). */
    public Pagina<Cliente> listar(String autorizacion, String nombre, int page, int size, String sort) {
        return ejecutar(rest.get().uri(b -> b.path("/api/v1/clientes")
                .queryParamIfPresent("nombre", Optional.ofNullable(nombre))
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .build()), autorizacion, PAGINA);
    }

    /** POST /clientes (400 con detalle por campo si los datos no son válidos; 409 si ya existe). */
    public Cliente crear(String autorizacion, ClienteInput input) {
        return ejecutar(rest.post().uri("/api/v1/clientes").contentType(MediaType.APPLICATION_JSON).body(input),
                autorizacion, Cliente.class);
    }
}
