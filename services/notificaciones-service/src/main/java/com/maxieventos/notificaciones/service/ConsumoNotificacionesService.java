package com.maxieventos.notificaciones.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.notificaciones.config.KafkaConsumerConfig;
import com.maxieventos.notificaciones.entity.EventoProcesado;
import com.maxieventos.notificaciones.entity.Notificacion;
import com.maxieventos.notificaciones.kafka.DatosEventoConfirmado;
import com.maxieventos.notificaciones.kafka.DatosEventoProximo;
import com.maxieventos.notificaciones.kafka.DatosPagoRegistrado;
import com.maxieventos.notificaciones.kafka.MensajeInvalidoException;
import com.maxieventos.notificaciones.kafka.MensajeKafka;
import com.maxieventos.notificaciones.kafka.RegistroConsumo.Resultado;
import com.maxieventos.notificaciones.repository.EventoProcesadoRepository;
import com.maxieventos.notificaciones.repository.NotificacionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Genera una notificación simulada (estado GENERADA, destinatario {@code cliente:<clienteId>}) por cada EVENTO_CONFIRMADO
 * y PAGO_REGISTRADO. Idempotencia con {@code notificaciones_eventos_procesados} (idEvento, consumerGroup), en la misma
 * transacción que la notificación.
 */
@Service
public class ConsumoNotificacionesService {
    private static final String GRUPO = KafkaConsumerConfig.GRUPO;

    private final NotificacionRepository notificacionRepository;
    private final EventoProcesadoRepository procesadoRepository;
    private final RedactorNotificaciones redactor;
    private final TransactionTemplate transaccion;
    private final ObjectMapper objectMapper;

    public ConsumoNotificacionesService(NotificacionRepository notificacionRepository,
                                        EventoProcesadoRepository procesadoRepository, RedactorNotificaciones redactor,
                                        TransactionTemplate transaccion, ObjectMapper objectMapper) {
        this.notificacionRepository = notificacionRepository;
        this.procesadoRepository = procesadoRepository;
        this.redactor = redactor;
        this.transaccion = transaccion;
        this.objectMapper = objectMapper;
    }

    public Resultado procesarEventoConfirmado(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka mensaje = registro.value();
        DatosEventoConfirmado d = leer(mensaje, DatosEventoConfirmado.TIPO, DatosEventoConfirmado.class);
        if (!mensaje.eventoId().equals(d.eventoId()) || d.clienteId() == null || d.nombreEvento() == null
                || d.nombreEvento().isBlank() || d.fecha() == null) {
            throw new MensajeInvalidoException("Los datos de EVENTO_CONFIRMADO no son válidos (eventoId, clienteId, "
                    + "nombreEvento o fecha)");
        }
        return guardar(registro, d.clienteId(), redactor.eventoConfirmado(d));
    }

    public Resultado procesarPagoRegistrado(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka mensaje = registro.value();
        DatosPagoRegistrado d = leer(mensaje, DatosPagoRegistrado.TIPO, DatosPagoRegistrado.class);
        if (!mensaje.eventoId().equals(d.eventoId()) || d.clienteId() == null || d.monto() == null
                || d.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new MensajeInvalidoException("Los datos de PAGO_REGISTRADO no son válidos (eventoId, clienteId o monto)");
        }
        return guardar(registro, d.clienteId(), redactor.pagoRegistrado(d, mensaje.fechaOcurrencia()));
    }

    /** Fase 11E: EVENTO_PROXIMO → "Faltan N días para su evento…". diasRestantes debe ser ≥ 1 (un recordatorio del futuro). */
    public Resultado procesarEventoProximo(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka mensaje = registro.value();
        DatosEventoProximo d = leer(mensaje, DatosEventoProximo.TIPO, DatosEventoProximo.class);
        if (!mensaje.eventoId().equals(d.eventoId()) || d.clienteId() == null || d.nombreEvento() == null
                || d.nombreEvento().isBlank() || d.fecha() == null || d.diasRestantes() == null || d.diasRestantes() < 1) {
            throw new MensajeInvalidoException("Los datos de EVENTO_PROXIMO no son válidos (eventoId, clienteId, "
                    + "nombreEvento, fecha o diasRestantes ≥ 1)");
        }
        return guardar(registro, d.clienteId(), redactor.eventoProximo(d));
    }

    private Resultado guardar(ConsumerRecord<String, MensajeKafka> registro, UUID clienteId,
                              RedactorNotificaciones.Texto texto) {
        MensajeKafka mensaje = registro.value();
        EventoProcesado.Clave clave = new EventoProcesado.Clave(mensaje.idEvento(), GRUPO);
        String destinatario = RedactorNotificaciones.destinatario(clienteId);
        try {
            return transaccion.execute(estado -> {
                if (procesadoRepository.existsById(clave)) {
                    return Resultado.duplicado("idEvento ya procesado por " + GRUPO);
                }
                Instant ahora = Instant.now();
                notificacionRepository.save(new Notificacion(mensaje.idEvento(), mensaje.eventoId(), mensaje.tipo(),
                        destinatario, texto.asunto(), texto.mensaje(), ahora));
                procesadoRepository.save(new EventoProcesado(mensaje.idEvento(), GRUPO, mensaje.tipo(), mensaje.eventoId(),
                        registro.topic(), registro.partition(), registro.offset(), ahora));
                return Resultado.procesado("notificación GENERADA para " + destinatario + ": " + texto.asunto());
            });
        } catch (DataIntegrityViolationException ex) {
            if (procesadoRepository.existsById(clave)) {
                return Resultado.duplicado("idEvento ya procesado por " + GRUPO + " (detectado por la BD)");
            }
            throw ex;
        }
    }

    private <T> T leer(MensajeKafka mensaje, String tipoEsperado, Class<T> clase) {
        if (mensaje == null) {
            throw new MensajeInvalidoException("Mensaje vacío");
        }
        if (mensaje.idEvento() == null || mensaje.eventoId() == null || mensaje.datos() == null) {
            throw new MensajeInvalidoException("Sobre incompleto: faltan idEvento, eventoId o datos");
        }
        if (!tipoEsperado.equals(mensaje.tipo())) {
            throw new MensajeInvalidoException("Tipo de mensaje no esperado en este tópico: " + mensaje.tipo()
                    + " (se esperaba " + tipoEsperado + ")");
        }
        try {
            return objectMapper.treeToValue(mensaje.datos(), clase);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new MensajeInvalidoException("Los datos de " + tipoEsperado + " no tienen el formato esperado", ex);
        }
    }
}
