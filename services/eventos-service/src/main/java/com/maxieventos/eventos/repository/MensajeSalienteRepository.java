package com.maxieventos.eventos.repository;

import com.maxieventos.eventos.entity.MensajeSaliente;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MensajeSalienteRepository extends JpaRepository<MensajeSaliente, UUID> {

    /**
     * Toma la fila para enviarla: UPDATE atómico que solo afecta a una fila PENDIENTE (o ENVIANDO "atascada" desde antes de
     * {@code atascadoAntesDe}). Devuelve 1 si este proceso la tomó y 0 si ya la tomó otro (envío inmediato o reenvío):
     * así nunca se publica la misma fila dos veces a la vez.
     */
    @Modifying
    @Query("update MensajeSaliente m set m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.ENVIANDO,"
            + " m.intentos = m.intentos + 1, m.tomadoEn = :ahora, m.actualizadoEn = :ahora"
            + " where m.idEvento = :id and (m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.PENDIENTE"
            + " or (m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.ENVIANDO and m.tomadoEn < :atascadoAntesDe))")
    int tomar(@Param("id") UUID id, @Param("ahora") Instant ahora, @Param("atascadoAntesDe") Instant atascadoAntesDe);

    @Modifying
    @Query("update MensajeSaliente m set m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.PUBLICADO,"
            + " m.publicadoEn = :ahora, m.actualizadoEn = :ahora, m.ultimoError = null"
            + " where m.idEvento = :id and m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.ENVIANDO")
    int marcarPublicado(@Param("id") UUID id, @Param("ahora") Instant ahora);

    @Modifying
    @Query("update MensajeSaliente m set m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.PENDIENTE,"
            + " m.ultimoError = :error, m.actualizadoEn = :ahora"
            + " where m.idEvento = :id and m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.ENVIANDO")
    int devolverAPendiente(@Param("id") UUID id, @Param("error") String error, @Param("ahora") Instant ahora);

    /** Candidatos al reenvío, del más antiguo al más nuevo (orden de los hechos). */
    @Query("select m.idEvento from MensajeSaliente m"
            + " where (m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.PENDIENTE and m.actualizadoEn < :pendienteAntesDe)"
            + " or (m.estado = com.maxieventos.eventos.entity.EstadoMensajeSaliente.ENVIANDO and m.tomadoEn < :atascadoAntesDe)"
            + " order by m.creadoEn")
    List<UUID> candidatosAReenvio(@Param("pendienteAntesDe") Instant pendienteAntesDe,
                                  @Param("atascadoAntesDe") Instant atascadoAntesDe, Pageable lote);
}
