package com.maxieventos.logistica.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.logistica.config.EfectosAdicionales;
import com.maxieventos.logistica.config.KafkaConsumerConfig;
import com.maxieventos.logistica.config.RedisCacheConfig;
import com.maxieventos.logistica.entity.AdicionalLogistica;
import com.maxieventos.logistica.entity.EventoProcesado;
import com.maxieventos.logistica.entity.ItemCompraEntity;
import com.maxieventos.logistica.entity.LogisticaEvento;
import com.maxieventos.logistica.kafka.DatosEventoConfirmado;
import com.maxieventos.logistica.kafka.DatosServicioAdicionalAgregado;
import com.maxieventos.logistica.kafka.MensajeInvalidoException;
import com.maxieventos.logistica.kafka.MensajeKafka;
import com.maxieventos.logistica.kafka.RegistroConsumo.Resultado;
import com.maxieventos.logistica.repository.AdicionalLogisticaRepository;
import com.maxieventos.logistica.repository.EventoProcesadoRepository;
import com.maxieventos.logistica.repository.LogisticaEventoRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesa los mensajes Kafka que consume Logística.
 * <ul>
 *   <li><b>Idempotencia:</b> la tabla {@code logistica_eventos_procesados} (idEvento, consumerGroup) es la protección contra
 *       duplicados para TODOS los tipos; la fila se inserta en la misma transacción que el cambio. Un duplicado se ignora.</li>
 *   <li><b>EVENTO_CONFIRMADO:</b> calcula meseros, cocina, transporte y compras. Además, si el evento YA tiene logística
 *       (otra confirmación del mismo evento) se ignora: esta regla aplica solo a este tipo.</li>
 *   <li><b>SERVICIO_ADICIONAL_AGREGADO:</b> se guarda siempre; si el evento ya tiene logística se aplica su efecto
 *       ({@link EfectosAdicionales}) y se invalida la caché; si no, se aplica al llegar la confirmación.</li>
 *   <li>Un mensaje que no cumple el contrato lanza {@link MensajeInvalidoException} (va al DLT sin reintentos).</li>
 * </ul>
 */
@Service
public class ConsumoLogisticaService {
    private static final Set<String> TIPOS_EVENTO = Set.of("BODA", "QUINCE_ANOS");
    private static final Set<String> ZONAS = Set.of("NEIVA", "RURAL");
    private static final String GRUPO = KafkaConsumerConfig.GRUPO;

    private final LogisticaEventoRepository logisticaRepository;
    private final AdicionalLogisticaRepository adicionalRepository;
    private final EventoProcesadoRepository procesadoRepository;
    private final TransactionTemplate transaccion;
    private final EfectosAdicionales efectos;
    private final ObjectMapper objectMapper;

    public ConsumoLogisticaService(LogisticaEventoRepository logisticaRepository,
                                   AdicionalLogisticaRepository adicionalRepository,
                                   EventoProcesadoRepository procesadoRepository, TransactionTemplate transaccion,
                                   EfectosAdicionales efectos, ObjectMapper objectMapper) {
        this.logisticaRepository = logisticaRepository;
        this.adicionalRepository = adicionalRepository;
        this.procesadoRepository = procesadoRepository;
        this.transaccion = transaccion;
        this.efectos = efectos;
        this.objectMapper = objectMapper;
    }

    public Resultado procesarEventoConfirmado(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka mensaje = registro.value();
        DatosEventoConfirmado datos = validarEventoConfirmado(mensaje);
        return idempotente(registro, () -> {
            if (logisticaRepository.existsById(datos.eventoId())) {
                procesadoRepository.save(procesado(registro));
                return Resultado.duplicado("el evento ya tiene logística (otra confirmación del mismo evento)");
            }
            int invitados = datos.numInvitados();
            LogisticaEvento logistica = new LogisticaEvento(datos.eventoId(), dividirRedondeandoArriba(invitados, 15),
                    dividirRedondeandoArriba(invitados, 30), invitados <= 100 ? 1 : 2);
            generarCompras(datos).forEach(logistica::agregarCompra);
            List<AdicionalLogistica> pendientes = adicionalRepository.findByEventoIdAndAplicadoFalse(datos.eventoId());
            pendientes.forEach(adicional -> adicional.aplicarA(logistica));
            logisticaRepository.save(logistica);
            procesadoRepository.save(procesado(registro));
            return Resultado.procesado("meseros=" + logistica.getMeserosRequeridos() + ", cocina="
                    + logistica.getPersonalCocinaRequerido() + ", camiones=" + logistica.getCamionesTransporte()
                    + ", compras=" + logistica.getCompras().size() + ", adicionalesAplicados=" + pendientes.size());
        });
    }

    /** La caché del evento se invalida al terminar (después del commit), también si era un duplicado (inocuo). */
    @CacheEvict(value = RedisCacheConfig.LOGISTICA_REQUERIMIENTOS, key = "#registro.value().eventoId()")
    public Resultado procesarServicioAdicional(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka mensaje = registro.value();
        DatosServicioAdicionalAgregado datos = validarServicioAdicional(mensaje);
        EfectosAdicionales.Efecto efecto = efectos.de(datos.codigo());
        int cantidad = datos.cantidad();
        return idempotente(registro, () -> {
            AdicionalLogistica adicional = new AdicionalLogistica(datos.adicionalId(), datos.eventoId(), datos.codigo(),
                    cantidad, efecto.meserosPorUnidad() * cantidad, efecto.cocinaPorUnidad() * cantidad,
                    efecto.camionesPorUnidad() * cantidad, Instant.now());
            Optional<LogisticaEvento> logistica = logisticaRepository.findById(datos.eventoId());
            String detalle;
            if (logistica.isEmpty()) {
                detalle = "el evento aún no tiene logística; el adicional " + datos.codigo() + " se aplicará al confirmarlo";
            } else if (efecto.sinEfecto()) {
                adicional.aplicarA(logistica.get());
                detalle = "adicional " + datos.codigo() + " sin efecto en logística";
            } else {
                LogisticaEvento l = logistica.get();
                String antes = l.getMeserosRequeridos() + "/" + l.getPersonalCocinaRequerido() + "/" + l.getCamionesTransporte();
                adicional.aplicarA(l);
                logisticaRepository.save(l);
                detalle = "logística recalculada por " + datos.codigo() + " x" + cantidad + ": meseros/cocina/camiones "
                        + antes + " -> " + l.getMeserosRequeridos() + "/" + l.getPersonalCocinaRequerido() + "/"
                        + l.getCamionesTransporte();
            }
            adicionalRepository.save(adicional);
            procesadoRepository.save(procesado(registro));
            return Resultado.procesado(detalle);
        });
    }

    /**
     * Ejecuta el procesamiento en una transacción que incluye el registro en logistica_eventos_procesados. Si el idEvento
     * ya se procesó en este grupo → DUPLICADO_IGNORADO (también si la restricción de la BD detecta una carrera).
     */
    private Resultado idempotente(ConsumerRecord<String, MensajeKafka> registro, java.util.function.Supplier<Resultado> trabajo) {
        EventoProcesado.Clave clave = new EventoProcesado.Clave(registro.value().idEvento(), GRUPO);
        try {
            return transaccion.execute(estado -> procesadoRepository.existsById(clave)
                    ? Resultado.duplicado("idEvento ya procesado por " + GRUPO) : trabajo.get());
        } catch (DataIntegrityViolationException ex) {
            if (procesadoRepository.existsById(clave)) {
                return Resultado.duplicado("idEvento ya procesado por " + GRUPO + " (detectado por la BD)");
            }
            throw ex;
        }
    }

    private static EventoProcesado procesado(ConsumerRecord<String, MensajeKafka> registro) {
        MensajeKafka m = registro.value();
        return new EventoProcesado(m.idEvento(), GRUPO, m.tipo(), m.eventoId(), registro.topic(), registro.partition(),
                registro.offset(), Instant.now());
    }

    // ------------------------------------------------------------------ validación del contrato del mensaje

    private DatosEventoConfirmado validarEventoConfirmado(MensajeKafka mensaje) {
        validarSobre(mensaje, DatosEventoConfirmado.TIPO);
        DatosEventoConfirmado d = datos(mensaje, DatosEventoConfirmado.class);
        if (!mensaje.eventoId().equals(d.eventoId()) || d.tipoEvento() == null || !TIPOS_EVENTO.contains(d.tipoEvento())
                || d.numInvitados() == null || d.numInvitados() <= 0 || d.zona() == null || !ZONAS.contains(d.zona())) {
            throw new MensajeInvalidoException("Los datos de EVENTO_CONFIRMADO no son válidos (eventoId, tipoEvento, "
                    + "numInvitados o zona)");
        }
        return d;
    }

    private DatosServicioAdicionalAgregado validarServicioAdicional(MensajeKafka mensaje) {
        validarSobre(mensaje, DatosServicioAdicionalAgregado.TIPO);
        DatosServicioAdicionalAgregado d = datos(mensaje, DatosServicioAdicionalAgregado.class);
        if (d.adicionalId() == null || !mensaje.eventoId().equals(d.eventoId()) || d.codigo() == null
                || d.codigo().isBlank() || d.cantidad() == null || d.cantidad() <= 0) {
            throw new MensajeInvalidoException("Los datos de SERVICIO_ADICIONAL_AGREGADO no son válidos (adicionalId, "
                    + "eventoId, codigo o cantidad)");
        }
        return d;
    }

    private static void validarSobre(MensajeKafka mensaje, String tipoEsperado) {
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
    }

    private <T> T datos(MensajeKafka mensaje, Class<T> tipo) {
        try {
            return objectMapper.treeToValue(mensaje.datos(), tipo);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new MensajeInvalidoException("Los datos de " + mensaje.tipo() + " no tienen el formato esperado", ex);
        }
    }

    // ------------------------------------------------------------------ cálculo (sin cambios desde la Fase 3)

    private static int dividirRedondeandoArriba(int cantidad, int divisor) {
        return (cantidad + divisor - 1) / divisor;
    }

    private static List<ItemCompraEntity> generarCompras(DatosEventoConfirmado evento) {
        int invitados = evento.numInvitados();
        int factorHielo = evento.zona().equals("RURAL") ? 8 : 10;
        List<ItemCompraEntity> compras = new ArrayList<>(List.of(
                new ItemCompraEntity("Hielo", dividirRedondeandoArriba(invitados, factorHielo) + " bolsas"),
                new ItemCompraEntity("Servilletas", (invitados * 3) + " unidades"),
                new ItemCompraEntity("Bebidas", (invitados * 2) + " unidades"),
                new ItemCompraEntity("Vasos", (invitados * 2) + " unidades")));
        if (evento.tipoEvento().equals("BODA")) {
            compras.add(new ItemCompraEntity("Copas", dividirRedondeandoArriba(invitados, 2) + " unidades"));
        }
        return compras;
    }
}
