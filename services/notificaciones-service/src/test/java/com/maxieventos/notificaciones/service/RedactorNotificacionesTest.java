package com.maxieventos.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.maxieventos.notificaciones.kafka.DatosEventoConfirmado;
import com.maxieventos.notificaciones.kafka.DatosEventoProximo;
import com.maxieventos.notificaciones.kafka.DatosPagoRegistrado;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Los textos van en español, con tildes, y las fechas y montos en formato es-CO. */
class RedactorNotificacionesTest {

    private final RedactorNotificaciones redactor = new RedactorNotificaciones("America/Bogota");

    @Test
    void eventoProximoEnPluralYSingular() {
        var siete = new DatosEventoProximo(UUID.randomUUID(), "Quince años de Sofía Muñoz", "QUINCE_ANOS",
                LocalDate.of(2026, 12, 5), UUID.randomUUID(), 7, 7);
        var uno = new DatosEventoProximo(UUID.randomUUID(), "Boda de Ana y Luis", "BODA",
                LocalDate.of(2027, 1, 16), UUID.randomUUID(), 1, 1);

        RedactorNotificaciones.Texto texto = redactor.eventoProximo(siete);

        assertThat(texto.asunto()).isEqualTo("Faltan 7 días para su evento: Quince años de Sofía Muñoz");
        assertThat(texto.mensaje()).isEqualTo("Hola. Faltan 7 días para su evento «Quince años de Sofía Muñoz» (Quince años) "
                + "del sábado 5 de diciembre de 2026. Estamos preparando todo para ese día; si necesita algo, comuníquese "
                + "con la organizadora. Gracias por confiar en Maxi Eventos.");
        assertThat(redactor.eventoProximo(uno).asunto()).isEqualTo("Falta 1 día para su evento: Boda de Ana y Luis");
        assertThat(redactor.eventoProximo(uno).mensaje()).startsWith("Hola. Falta 1 día para su evento «Boda de Ana y Luis» "
                + "(Boda) del sábado 16 de enero de 2027.");
    }

    @Test
    void eventoConfirmado() {
        var datos = new DatosEventoConfirmado(UUID.randomUUID(), "Quince años de Sofía Muñoz", "QUINCE_ANOS",
                LocalDate.of(2026, 12, 5), UUID.randomUUID(), 90, 15, 105);

        RedactorNotificaciones.Texto texto = redactor.eventoConfirmado(datos);

        assertThat(texto.asunto()).isEqualTo("Evento confirmado: Quince años de Sofía Muñoz");
        assertThat(texto.mensaje()).isEqualTo("Hola. Le confirmamos que su evento «Quince años de Sofía Muñoz» (Quince años) "
                + "del sábado 5 de diciembre de 2026 quedó reservado para 105 invitados (90 adultos y 15 niños). "
                + "La fecha ya está apartada para usted. Gracias por confiar en Maxi Eventos.");
    }

    @Test
    void bodaSinDesgloseDeInvitados() {
        var datos = new DatosEventoConfirmado(UUID.randomUUID(), "Boda de Ana y Luis", "BODA",
                LocalDate.of(2027, 1, 16), UUID.randomUUID(), null, null, null);

        assertThat(redactor.eventoConfirmado(datos).mensaje())
                .contains("(Boda) del sábado 16 de enero de 2027 quedó reservado. La fecha");
    }

    @Test
    void pagoConMontoYHoraLocal() {
        var datos = new DatosPagoRegistrado(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("2610000"), "COP", "TRANSFERENCIA", Instant.parse("2026-10-28T15:30:00Z"));

        RedactorNotificaciones.Texto texto = redactor.pagoRegistrado(datos, null);

        assertThat(texto.asunto()).isEqualTo("Abono registrado");
        assertThat(texto.mensaje()).isEqualTo("Hola. Registramos su abono de 2.610.000,00 COP (TRANSFERENCIA) el 28 de "
                + "octubre de 2026 a las 10:30. Gracias por su pago; puede consultar su saldo con la organizadora.");
    }

    @Test
    void pagoSinFechaUsaLaDelSobre() {
        var datos = new DatosPagoRegistrado(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("500000.5"), "COP", null, null);

        assertThat(redactor.pagoRegistrado(datos, Instant.parse("2026-11-02T01:00:00Z")).mensaje())
                .startsWith("Hola. Registramos su abono de 500.000,50 COP el 1 de noviembre de 2026 a las 20:00.");
    }

    @Test
    void destinatarioEsElCliente() {
        UUID clienteId = UUID.fromString("3c9e0d7a-0000-4000-8000-000000000001");
        assertThat(RedactorNotificaciones.destinatario(clienteId)).isEqualTo("cliente:3c9e0d7a-0000-4000-8000-000000000001");
    }
}
