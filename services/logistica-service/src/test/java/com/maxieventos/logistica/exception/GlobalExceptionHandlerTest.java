package com.maxieventos.logistica.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/** El handler nunca devuelve mensajes internos (SQL, nombres de clases, trazas): solo un mensaje neutro. */
class GlobalExceptionHandlerTest {

    private static final String DETALLE_INTERNO = "ERROR: duplicate key value violates constraint pk_item_compras";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/eventos/1/compras");

    @Test
    void errorGenericoDevuelveMensajeNeutro() {
        ResponseEntity<Object> respuesta = handler.manejarGenerico(
                new IllegalStateException(DETALLE_INTERNO, new RuntimeException("org.hibernate.SQL")), request);

        ErrorResponse cuerpo = (ErrorResponse) respuesta.getBody();
        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
        assertThat(cuerpo.message()).isEqualTo("Ocurrió un error interno. Intenta de nuevo más tarde.");
        assertThat(cuerpo.toString()).doesNotContain(DETALLE_INTERNO).doesNotContain("hibernate");
    }

    @Test
    void integridadDeDatosNoExponeLaRestriccion() {
        ResponseEntity<Object> respuesta = handler.manejarIntegridad(
                new DataIntegrityViolationException(DETALLE_INTERNO), request);

        ErrorResponse cuerpo = (ErrorResponse) respuesta.getBody();
        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(cuerpo.toString()).doesNotContain(DETALLE_INTERNO);
    }
}
