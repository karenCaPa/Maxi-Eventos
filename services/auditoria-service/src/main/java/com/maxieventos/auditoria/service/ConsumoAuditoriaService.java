package com.maxieventos.auditoria.service;

import com.maxieventos.auditoria.config.KafkaConsumerConfig;
import com.maxieventos.auditoria.entity.AuditoriaEvento;
import com.maxieventos.auditoria.entity.EventoProcesado;
import com.maxieventos.auditoria.entity.TipoMensaje;
import com.maxieventos.auditoria.kafka.MensajeInvalidoException;
import com.maxieventos.auditoria.kafka.MensajeKafka;
import com.maxieventos.auditoria.kafka.RegistroConsumo.Resultado;
import com.maxieventos.auditoria.repository.AuditoriaEventoRepository;
import com.maxieventos.auditoria.repository.EventoProcesadoRepository;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guarda cada mensaje válido de los tres tópicos en {@code auditoria_eventos}, con el JSON original como payload.
 * Idempotencia con {@code auditoria_eventos_procesados} (idEvento, consumerGroup), en la misma transacción.
 * Un sobre incompleto o con un tipo que no corresponde al tópico es inválido y va al DLT (no se audita).
 */
@Service
public class ConsumoAuditoriaService {
    private static final String GRUPO = KafkaConsumerConfig.GRUPO;

    private final AuditoriaEventoRepository auditoriaRepository;
    private final EventoProcesadoRepository procesadoRepository;
    private final TransactionTemplate transaccion;

    public ConsumoAuditoriaService(AuditoriaEventoRepository auditoriaRepository,
                                   EventoProcesadoRepository procesadoRepository, TransactionTemplate transaccion) {
        this.auditoriaRepository = auditoriaRepository;
        this.procesadoRepository = procesadoRepository;
        this.transaccion = transaccion;
    }

    public Resultado procesar(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka mensaje = registro.value();
        validar(mensaje, TipoMensaje.deTopico(registro.topic()));
        EventoProcesado.Clave clave = new EventoProcesado.Clave(mensaje.idEvento(), GRUPO);
        try {
            return transaccion.execute(estado -> {
                if (procesadoRepository.existsById(clave)) {
                    return Resultado.duplicado("idEvento ya procesado por " + GRUPO);
                }
                Instant ahora = Instant.now();
                auditoriaRepository.save(new AuditoriaEvento(mensaje.idEvento(), mensaje.tipo(), mensaje.eventoId(),
                        mensaje.servicioOrigen(), mensaje.textoOriginal(), registro.topic(), registro.partition(),
                        registro.offset(), mensaje.fechaOcurrencia(), ahora));
                procesadoRepository.save(new EventoProcesado(mensaje.idEvento(), GRUPO, mensaje.tipo(), mensaje.eventoId(),
                        registro.topic(), registro.partition(), registro.offset(), ahora));
                return Resultado.procesado("registrado en auditoria_eventos (origen " + mensaje.servicioOrigen() + ")");
            });
        } catch (DataIntegrityViolationException ex) {
            if (procesadoRepository.existsById(clave)) {
                return Resultado.duplicado("idEvento ya procesado por " + GRUPO + " (detectado por la BD)");
            }
            throw ex;
        }
    }

    private void validar(MensajeKafka mensaje, TipoMensaje esperado) {
        if (mensaje == null) {
            throw new MensajeInvalidoException("Mensaje vacío");
        }
        if (mensaje.idEvento() == null || mensaje.eventoId() == null || mensaje.tipo() == null || mensaje.datos() == null) {
            throw new MensajeInvalidoException("Sobre incompleto: faltan idEvento, tipo, eventoId o datos");
        }
        if (!esperado.name().equals(mensaje.tipo())) {
            throw new MensajeInvalidoException("Tipo de mensaje no esperado en este tópico: " + mensaje.tipo()
                    + " (se esperaba " + esperado.name() + ")");
        }
        if (mensaje.textoOriginal() == null || mensaje.textoOriginal().isBlank()) {
            throw new MensajeInvalidoException("No se recibió el JSON original del mensaje");
        }
    }
}
