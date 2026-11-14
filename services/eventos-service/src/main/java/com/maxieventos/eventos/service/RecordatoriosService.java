package com.maxieventos.eventos.service;

import com.maxieventos.eventos.dto.ResultadoRecordatoriosResponse;
import com.maxieventos.eventos.dto.ResultadoRecordatoriosResponse.Recordatorio;
import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.Evento;
import com.maxieventos.eventos.entity.RecordatorioEnviado;
import com.maxieventos.eventos.kafka.EventoProximoInterno;
import com.maxieventos.eventos.repository.EventoRepository;
import com.maxieventos.eventos.repository.RecordatorioEnviadoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recordatorios de eventos próximos (Fase 11E). Revisa los eventos RESERVADOS con fecha entre MAÑANA y dentro del umbral
 * mayor ({@code RECORDATORIOS_DIAS}, por defecto 7) y, para cada uno, el umbral más pequeño que aplica
 * ({@link UmbralesRecordatorio#aplicable}). Si ese recordatorio no se envió antes:
 * <ul>
 *   <li>en UNA transacción por evento guarda la fila de {@code eventos_recordatorios_enviados} y publica el
 *       {@link EventoProximoInterno}, que el outbox guarda en esa misma transacción y envía a Kafka tras el commit;</li>
 *   <li>si dos ejecuciones coinciden (la programada y la manual), la restricción única (evento, umbral) y la clave del
 *       outbox (idEvento determinista) rechazan la segunda: se cuenta como YA_ENVIADO, sin duplicar el mensaje.</li>
 * </ul>
 * PENDIENTES y CANCELADOS no reciben recordatorio; un evento de hoy o pasado, tampoco.
 */
@Service
public class RecordatoriosService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordatoriosService.class);

    public static final String ENVIADO = "ENVIADO";
    public static final String YA_ENVIADO = "YA_ENVIADO";

    private final EventoRepository eventos;
    private final RecordatorioEnviadoRepository enviados;
    private final ApplicationEventPublisher publicador;
    private final Clock reloj;
    private final UmbralesRecordatorio umbrales;
    private final TransactionTemplate transaccion;

    public RecordatoriosService(EventoRepository eventos, RecordatorioEnviadoRepository enviados,
                                ApplicationEventPublisher publicador, Clock reloj, UmbralesRecordatorio umbrales,
                                PlatformTransactionManager transactionManager) {
        this.eventos = eventos;
        this.enviados = enviados;
        this.publicador = publicador;
        this.reloj = reloj;
        this.umbrales = umbrales;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /** @param origen PROGRAMADA (la tarea diaria) o MANUAL (la demostración, solo ADMINISTRADOR); solo para el log. */
    public ResultadoRecordatoriosResponse ejecutar(String origen) {
        LocalDate hoy = LocalDate.now(reloj);
        List<Evento> candidatos = eventos.findByEstadoAndFechaBetweenOrderByFechaAsc(EstadoEvento.RESERVADO,
                hoy.plusDays(1), hoy.plusDays(umbrales.maximo()));
        List<Recordatorio> resultado = new ArrayList<>();
        int nuevos = 0;
        for (Evento evento : candidatos) {
            int diasRestantes = (int) ChronoUnit.DAYS.between(hoy, evento.getFecha());
            OptionalInt umbral = umbrales.aplicable(diasRestantes);
            if (umbral.isEmpty()) {
                continue;
            }
            boolean enviado = enviar(evento, diasRestantes, umbral.getAsInt());
            if (enviado) {
                nuevos++;
            }
            resultado.add(new Recordatorio(evento.getId(), evento.getNombreEvento(), evento.getFecha(), diasRestantes,
                    umbral.getAsInt(), enviado ? ENVIADO : YA_ENVIADO));
            LOGGER.info("Recordatorio EVENTO_PROXIMO. origen={}, eventoId={}, fecha={}, diasRestantes={}, umbral={}, resultado={}",
                    origen, evento.getId(), evento.getFecha(), diasRestantes, umbral.getAsInt(), enviado ? ENVIADO : YA_ENVIADO);
        }
        LOGGER.info("Revisión de recordatorios. origen={}, hoy={}, umbrales={}, revisados={}, enviados={}, yaEnviados={}",
                origen, hoy, umbrales.dias(), candidatos.size(), nuevos, resultado.size() - nuevos);
        return new ResultadoRecordatoriosResponse(Instant.now(reloj), hoy, umbrales.dias(), candidatos.size(), nuevos,
                resultado.size() - nuevos, resultado);
    }

    /** true si se envió ahora; false si ya estaba enviado (antes o por otra ejecución simultánea). */
    private boolean enviar(Evento evento, int diasRestantes, int umbral) {
        if (enviados.existsByEventoIdAndUmbralDias(evento.getId(), umbral)) {
            return false;
        }
        try {
            transaccion.executeWithoutResult(estado -> {
                enviados.saveAndFlush(new RecordatorioEnviado(EventoProximoInterno.idDeterminista(evento.getId(), umbral),
                        evento.getId(), umbral, diasRestantes, evento.getFecha(), Instant.now(reloj)));
                publicador.publishEvent(EventoProximoInterno.de(evento, diasRestantes, umbral));
            });
            return true;
        } catch (DataIntegrityViolationException ex) {
            LOGGER.info("Recordatorio ya registrado por otra ejecución. eventoId={}, umbral={}", evento.getId(), umbral);
            return false;
        }
    }
}
