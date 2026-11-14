package com.maxieventos.gateway.service.remoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.maxieventos.gateway.dto.MiNotificacion;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Fase 11H: lo que el gateway envía a notificaciones-service para la campanita (rutas, métodos y el token tal cual). */
class NotificacionesRemotoTest {

    private MockRestServiceServer servidor;
    private NotificacionesClient notificaciones;

    @BeforeEach
    void crear() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://servicio");
        servidor = MockRestServiceServer.bindTo(builder).build();
        notificaciones = new NotificacionesClient(builder.build());
    }

    @Test
    void lasCuatroOperacionesUsanSuRutaYElToken() {
        UUID id = UUID.fromString("5b0c9f3e-7a2d-4c1b-9e8f-0a1b2c3d4e5f");
        String una = """
                {"id":"%s","idEvento":"%s","eventoId":"%s","tipo":"EVENTO_PROXIMO","destinatario":"cliente:c1",
                 "asunto":"Falta 1 día para su evento: Boda","mensaje":"Hola.","estado":"GENERADA",
                 "fechaCreacion":"2026-11-06T15:45:00.123456Z","leida":true,"leidaEn":"2026-11-06T16:00:00Z"}
                """.formatted(id, UUID.randomUUID(), UUID.randomUUID());
        servidor.expect(requestTo("http://servicio/api/v1/notificaciones/recientes?page=0&size=10"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("{\"contenido\":[" + una + "],\"pagina\":0,\"tamanoPagina\":10,\"totalElementos\":1,\"totalPaginas\":1}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://servicio/api/v1/notificaciones/sin-leer"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("{\"sinLeer\":2}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://servicio/api/v1/notificaciones/" + id + "/leida"))
                .andExpect(method(HttpMethod.PATCH)).andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess(una, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://servicio/api/v1/notificaciones/leidas"))
                .andExpect(method(HttpMethod.PATCH)).andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("{\"marcadas\":3}", MediaType.APPLICATION_JSON));

        assertThat(notificaciones.recientes("Bearer abc", 0, 10).contenido()).singleElement()
                .satisfies(n -> assertThat(n.leida()).isTrue());
        assertThat(notificaciones.sinLeer("Bearer abc")).isEqualTo(2);
        MiNotificacion leida = notificaciones.marcarLeida("Bearer abc", id);
        assertThat(leida.id()).isEqualTo(id);
        assertThat(leida.leidaEn()).isNotNull();
        assertThat(notificaciones.marcarTodas("Bearer abc")).isEqualTo(3);
        servidor.verify();
    }
}
