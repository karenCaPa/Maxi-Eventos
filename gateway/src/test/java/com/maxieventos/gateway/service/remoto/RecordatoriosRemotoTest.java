package com.maxieventos.gateway.service.remoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.maxieventos.gateway.dto.ResultadoRecordatorios;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Fase 11E: lo que el gateway envía a eventos-service para ejecutar los recordatorios y cómo lee la respuesta. */
class RecordatoriosRemotoTest {

    private MockRestServiceServer servidor;
    private EventosClient eventos;

    @BeforeEach
    void crear() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://servicio");
        servidor = MockRestServiceServer.bindTo(builder).build();
        eventos = new EventosClient(builder.build());
    }

    @Test
    void ejecutarRecordatoriosHacePostConElTokenYLeeElResultado() {
        UUID eventoId = UUID.fromString("5b0c9f3e-7a2d-4c1b-9e8f-0a1b2c3d4e5f");
        servidor.expect(requestTo("http://servicio/api/v1/recordatorios/ejecutar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("""
                        {"ejecutadoEn":"2026-11-05T12:00:00.123456Z","hoy":"2026-11-05","umbralesDias":[1,7],"revisados":1,
                         "enviados":1,"yaEnviados":0,"recordatorios":[{"eventoId":"%s","nombreEvento":"Quince años de Lucía",
                         "fecha":"2026-11-12","diasRestantes":7,"umbralDias":7,"resultado":"ENVIADO"}]}
                        """.formatted(eventoId), MediaType.APPLICATION_JSON));

        ResultadoRecordatorios resultado = eventos.ejecutarRecordatorios("Bearer abc");

        assertThat(resultado.hoy()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(resultado.ejecutadoEn()).isNotNull();
        assertThat(resultado.umbralesDias()).containsExactly(1, 7);
        assertThat(resultado.recordatorios()).containsExactly(new ResultadoRecordatorios.Recordatorio(eventoId,
                "Quince años de Lucía", LocalDate.of(2026, 11, 12), 7, 7, "ENVIADO"));
        servidor.verify();
    }

    @Test
    void elRechazoDelServicioLlegaComoErrorDeEventosConSuEstado() {
        servidor.expect(requestTo("http://servicio/api/v1/recordatorios/ejecutar"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"No tienes permiso para realizar esta operación.\","
                                + "\"path\":\"/api/v1/recordatorios/ejecutar\",\"errores\":[]}"));

        assertThatThrownBy(() -> eventos.ejecutarRecordatorios("Bearer abc"))
                .isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(403);
                    assertThat(ex.getServicio()).isEqualTo("eventos");
                    assertThat(ex.clasificacion()).hasToString("FORBIDDEN");
                });
        servidor.verify();
    }
}
