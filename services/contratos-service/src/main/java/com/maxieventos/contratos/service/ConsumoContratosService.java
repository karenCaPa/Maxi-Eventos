package com.maxieventos.contratos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.contratos.config.KafkaConsumerConfig;
import com.maxieventos.contratos.entity.ContratoAdicional;
import com.maxieventos.contratos.entity.EventoProcesado;
import com.maxieventos.contratos.kafka.DatosServicioAdicionalAgregado;
import com.maxieventos.contratos.kafka.MensajeInvalidoException;
import com.maxieventos.contratos.kafka.MensajeKafka;
import com.maxieventos.contratos.kafka.RegistroConsumo.Resultado;
import com.maxieventos.contratos.repository.ContratoAdicionalRepository;
import com.maxieventos.contratos.repository.ContratoRepository;
import com.maxieventos.contratos.repository.EventoProcesadoRepository;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesa SERVICIO_ADICIONAL_AGREGADO: guarda el adicional en {@code contrato_adicionales} (fuente del anexo), exista o no
 * el contrato todavía (si llega antes, aparece en el anexo cuando se genere; no se pierde ni va al DLT). Idempotencia con
 * {@code contratos_eventos_procesados} (idEvento, consumerGroup), en la misma transacción.
 */
@Service
public class ConsumoContratosService {
    private static final String GRUPO = KafkaConsumerConfig.GRUPO;

    private final ContratoAdicionalRepository adicionalRepository;
    private final ContratoRepository contratoRepository;
    private final EventoProcesadoRepository procesadoRepository;
    private final TransactionTemplate transaccion;
    private final ObjectMapper objectMapper;

    public ConsumoContratosService(ContratoAdicionalRepository adicionalRepository, ContratoRepository contratoRepository,
                                   EventoProcesadoRepository procesadoRepository, TransactionTemplate transaccion,
                                   ObjectMapper objectMapper) {
        this.adicionalRepository = adicionalRepository;
        this.contratoRepository = contratoRepository;
        this.procesadoRepository = procesadoRepository;
        this.transaccion = transaccion;
        this.objectMapper = objectMapper;
    }

    public Resultado procesarServicioAdicional(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka mensaje = registro.value();
        DatosServicioAdicionalAgregado datos = validar(mensaje);
        EventoProcesado.Clave clave = new EventoProcesado.Clave(mensaje.idEvento(), GRUPO);
        try {
            return transaccion.execute(estado -> {
                if (procesadoRepository.existsById(clave)) {
                    return Resultado.duplicado("idEvento ya procesado por " + GRUPO);
                }
                adicionalRepository.save(new ContratoAdicional(datos.adicionalId(), datos.eventoId(), datos.codigo(),
                        datos.nombre(), datos.cantidad(), datos.valorUnitario(), datos.subtotal(), datos.moneda(),
                        Instant.now()));
                procesadoRepository.save(new EventoProcesado(mensaje.idEvento(), GRUPO, mensaje.tipo(), mensaje.eventoId(),
                        registro.topic(), registro.partition(), registro.offset(), Instant.now()));
                return Resultado.procesado("adicional " + datos.codigo() + " x" + datos.cantidad() + " guardado para el anexo"
                        + (contratoRepository.findByEventoId(datos.eventoId()).isPresent() ? ""
                        : " (el evento aún no tiene contrato; aparecerá en el anexo cuando se genere)"));
            });
        } catch (DataIntegrityViolationException ex) {
            if (procesadoRepository.existsById(clave)) {
                return Resultado.duplicado("idEvento ya procesado por " + GRUPO + " (detectado por la BD)");
            }
            throw ex;
        }
    }

    private DatosServicioAdicionalAgregado validar(MensajeKafka mensaje) {
        if (mensaje == null) {
            throw new MensajeInvalidoException("Mensaje vacío");
        }
        if (mensaje.idEvento() == null || mensaje.eventoId() == null || mensaje.datos() == null) {
            throw new MensajeInvalidoException("Sobre incompleto: faltan idEvento, eventoId o datos");
        }
        if (!DatosServicioAdicionalAgregado.TIPO.equals(mensaje.tipo())) {
            throw new MensajeInvalidoException("Tipo de mensaje no esperado en este tópico: " + mensaje.tipo()
                    + " (se esperaba " + DatosServicioAdicionalAgregado.TIPO + ")");
        }
        DatosServicioAdicionalAgregado d;
        try {
            d = objectMapper.treeToValue(mensaje.datos(), DatosServicioAdicionalAgregado.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new MensajeInvalidoException("Los datos de SERVICIO_ADICIONAL_AGREGADO no tienen el formato esperado", ex);
        }
        if (d.adicionalId() == null || !mensaje.eventoId().equals(d.eventoId()) || d.codigo() == null
                || d.nombre() == null || d.cantidad() == null || d.cantidad() <= 0 || d.valorUnitario() == null
                || d.subtotal() == null) {
            throw new MensajeInvalidoException("Los datos de SERVICIO_ADICIONAL_AGREGADO no son válidos (adicionalId, "
                    + "eventoId, codigo, nombre, cantidad o valores)");
        }
        return d;
    }
}
