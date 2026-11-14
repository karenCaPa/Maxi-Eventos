package com.maxieventos.notificaciones.service;

import com.maxieventos.notificaciones.kafka.DatosEventoConfirmado;
import com.maxieventos.notificaciones.kafka.DatosEventoProximo;
import com.maxieventos.notificaciones.kafka.DatosPagoRegistrado;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redacta el asunto y el mensaje de cada notificación, en español y con fechas y montos en formato es-CO (el mismo
 * que usan el contrato y el anexo de contratos-service).
 */
@Component
public class RedactorNotificaciones {
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_CO);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy 'a las' HH:mm", ES_CO);

    private final ZoneId zona;

    public RedactorNotificaciones(@Value("${maxi-eventos.zona-horaria}") String zonaHoraria) {
        this.zona = ZoneId.of(zonaHoraria);
    }

    public record Texto(String asunto, String mensaje) {
    }

    public static String destinatario(UUID clienteId) {
        return "cliente:" + clienteId;
    }

    public Texto eventoConfirmado(DatosEventoConfirmado d) {
        String invitados = d.numInvitados() == null ? ""
                : " para " + d.numInvitados() + " invitados"
                + (d.cantidadAdultos() == null || d.cantidadNinos() == null ? ""
                : " (" + d.cantidadAdultos() + " adultos y " + d.cantidadNinos() + " niños)");
        String mensaje = "Hola. Le confirmamos que su evento «" + d.nombreEvento() + "» (" + tipoLegible(d.tipoEvento())
                + ") del " + FECHA_LARGA.format(d.fecha()) + " quedó reservado" + invitados
                + ". La fecha ya está apartada para usted. Gracias por confiar en Maxi Eventos.";
        return new Texto("Evento confirmado: " + d.nombreEvento(), mensaje);
    }

    public Texto pagoRegistrado(DatosPagoRegistrado d, Instant fechaOcurrencia) {
        Instant cuando = d.fechaPago() != null ? d.fechaPago() : fechaOcurrencia;
        String mensaje = "Hola. Registramos su abono de " + monto(d.monto()) + " " + (d.moneda() == null ? "" : d.moneda()).trim()
                + (d.metodoPago() == null ? "" : " (" + d.metodoPago() + ")")
                + (cuando == null ? "" : " el " + FECHA_HORA.format(cuando.atZone(zona)))
                + ". Gracias por su pago; puede consultar su saldo con la organizadora.";
        return new Texto("Abono registrado", mensaje);
    }

    /** Fase 11E: recordatorio de un evento próximo. */
    public Texto eventoProximo(DatosEventoProximo d) {
        int dias = d.diasRestantes();
        String faltan = dias == 1 ? "Falta 1 día" : "Faltan " + dias + " días";
        String mensaje = "Hola. " + faltan + " para su evento «" + d.nombreEvento() + "» (" + tipoLegible(d.tipoEvento())
                + ") del " + FECHA_LARGA.format(d.fecha()) + ". Estamos preparando todo para ese día; si necesita "
                + "algo, comuníquese con la organizadora. Gracias por confiar en Maxi Eventos.";
        return new Texto(faltan + " para su evento: " + d.nombreEvento(), mensaje);
    }

    private static String tipoLegible(String tipoEvento) {
        if ("QUINCE_ANOS".equals(tipoEvento)) {
            return "Quince años";
        }
        if ("BODA".equals(tipoEvento)) {
            return "Boda";
        }
        return tipoEvento;
    }

    private static String monto(BigDecimal valor) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ES_CO)).format(valor);
    }
}
