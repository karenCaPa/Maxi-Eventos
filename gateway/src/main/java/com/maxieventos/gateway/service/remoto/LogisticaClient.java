package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.ItemCompra;
import com.maxieventos.gateway.dto.ItemCompraDatosInput;
import com.maxieventos.gateway.dto.ItemCompraInput;
import com.maxieventos.gateway.dto.Logistica;
import com.maxieventos.gateway.dto.Pagina;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** logistica-service. */
@Component
public class LogisticaClient extends ClienteRemoto {
    private static final ParameterizedTypeReference<Pagina<ItemCompra>> PAGINA = new ParameterizedTypeReference<>() { };

    public LogisticaClient(@Qualifier("logisticaRestClient") RestClient rest) {
        super(rest, "logistica");
    }

    /** GET /eventos/{id}/logistica; el evento aún no está confirmado → {@code null}. */
    public Logistica requerimientos(String autorizacion, UUID eventoId) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}/logistica", eventoId), autorizacion, Logistica.class);
    }

    public Pagina<ItemCompra> compras(String autorizacion, UUID eventoId, int page, int size) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}/compras?page={page}&size={size}", eventoId, page, size),
                autorizacion, PAGINA);
    }

    /** PUT /eventos/{id}/compras con [{id, comprado}]; devuelve la página ya actualizada. Un ítem ajeno → 404 (NOT_FOUND). */
    public Pagina<ItemCompra> actualizarCompras(String autorizacion, UUID eventoId, List<ItemCompraInput> items, int page,
                                                int size) {
        return ejecutar(rest.put().uri("/api/v1/eventos/{id}/compras?page={page}&size={size}", eventoId, page, size)
                .contentType(MediaType.APPLICATION_JSON).body(items), autorizacion, PAGINA);
    }

    /** 10B3: POST /eventos/{id}/compras (ítem MANUAL; 201). 400 por campo; 404 sin logística; 409 nombre repetido. */
    public ItemCompra agregarItem(String autorizacion, UUID eventoId, ItemCompraDatosInput input) {
        return ejecutar(rest.post().uri("/api/v1/eventos/{id}/compras", eventoId).contentType(MediaType.APPLICATION_JSON)
                .body(input), autorizacion, ItemCompra.class);
    }

    /** 10B3: PUT /eventos/{id}/compras/{itemId} (200). 400; 404 ítem de otro evento; 409 nombre repetido. */
    public ItemCompra editarItem(String autorizacion, UUID eventoId, UUID itemId, ItemCompraDatosInput input) {
        return ejecutar(rest.put().uri("/api/v1/eventos/{id}/compras/{itemId}", eventoId, itemId)
                .contentType(MediaType.APPLICATION_JSON).body(input), autorizacion, ItemCompra.class);
    }

    /** 10B3: DELETE /eventos/{id}/compras/{itemId} (204). 404; 409 si el ítem es CALCULADO. */
    public void quitarItem(String autorizacion, UUID eventoId, UUID itemId) {
        ejecutar(rest.delete().uri("/api/v1/eventos/{id}/compras/{itemId}", eventoId, itemId), autorizacion, Void.class);
    }
}
