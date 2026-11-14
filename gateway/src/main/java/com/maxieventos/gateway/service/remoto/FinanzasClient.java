package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.Balance;
import com.maxieventos.gateway.dto.Cotizacion;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Pago;
import com.maxieventos.gateway.dto.PagoInput;
import com.maxieventos.gateway.dto.ServicioAdicional;
import com.maxieventos.gateway.dto.ServicioCatalogo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** finanzas-service: cotización, balance, pagos y servicios adicionales. */
@Component
public class FinanzasClient extends ClienteRemoto {
    static final String CABECERA_IDEMPOTENCIA = "X-Idempotency-Key";
    private static final ParameterizedTypeReference<Pagina<Pago>> PAGINA_PAGOS = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ServicioAdicional>> ADICIONALES = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ServicioCatalogo>> CATALOGO = new ParameterizedTypeReference<>() { };

    public FinanzasClient(@Qualifier("finanzasRestClient") RestClient rest) {
        super(rest, "finanzas");
    }

    /** GET /eventos/{id}/cotizacion; sin cotización → {@code null}. */
    public Cotizacion cotizacion(String autorizacion, UUID eventoId) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}/cotizacion", eventoId), autorizacion, Cotizacion.class);
    }

    /** GET /eventos/{id}/balance; sin cotización → {@code null}. */
    public Balance balance(String autorizacion, UUID eventoId) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}/balance", eventoId), autorizacion, Balance.class);
    }

    public Pagina<Pago> pagos(String autorizacion, UUID eventoId, int page, int size) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}/pagos?page={page}&size={size}", eventoId, page, size),
                autorizacion, PAGINA_PAGOS);
    }

    public List<ServicioAdicional> adicionales(String autorizacion, UUID eventoId) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}/adicionales", eventoId), autorizacion, ADICIONALES);
    }

    public List<ServicioCatalogo> catalogo(String autorizacion) {
        return ejecutar(rest.get().uri("/api/v1/servicios-adicionales"), autorizacion, CATALOGO);
    }

    /** POST /cotizaciones (201 nueva o 200 la existente: la cotización no se regenera). */
    public Cotizacion generarCotizacion(String autorizacion, UUID eventoId) {
        return ejecutar(rest.post().uri("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("eventoId", eventoId)), autorizacion, Cotizacion.class);
    }

    /** POST /finanzas/pagos; la clave de idempotencia (opcional) va en la cabecera X-Idempotency-Key. */
    public Pago registrarPago(String autorizacion, PagoInput input) {
        RestClient.RequestBodySpec peticion = rest.post().uri("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON);
        if (input.claveIdempotencia() != null && !input.claveIdempotencia().isBlank()) {
            peticion.header(CABECERA_IDEMPOTENCIA, input.claveIdempotencia());
        }
        return ejecutar(peticion.body(input.cuerpo()), autorizacion, Pago.class);
    }

    /** POST /eventos/{id}/adicionales. */
    public ServicioAdicional agregarAdicional(String autorizacion, UUID eventoId, String codigo, int cantidad) {
        return ejecutar(rest.post().uri("/api/v1/eventos/{id}/adicionales", eventoId).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("codigo", codigo, "cantidad", cantidad)), autorizacion, ServicioAdicional.class);
    }
}
