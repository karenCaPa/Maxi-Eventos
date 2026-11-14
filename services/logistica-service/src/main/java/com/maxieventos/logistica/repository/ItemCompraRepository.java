package com.maxieventos.logistica.repository;

import com.maxieventos.logistica.entity.ItemCompraEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemCompraRepository extends JpaRepository<ItemCompraEntity, UUID> {
    /** El orden lo define el {@link Pageable} (parámetro {@code sort}, por defecto {@code nombreItem}). */
    Page<ItemCompraEntity> findAllByLogisticaEventoEventoId(UUID eventoId, Pageable pageable);
    Optional<ItemCompraEntity> findByIdAndLogisticaEventoEventoId(UUID id, UUID eventoId);

    /** 10B3: ¿ya hay un ítem con ese nombre en el evento? Sin distinguir mayúsculas ni espacios extremos ({@code nombre} llega recortado). */
    @Query("select count(i) > 0 from ItemCompraEntity i where i.logisticaEvento.eventoId = :eventoId "
            + "and lower(trim(i.nombreItem)) = lower(:nombre)")
    boolean existeNombreEnEvento(@Param("eventoId") UUID eventoId, @Param("nombre") String nombre);

    /** Igual, sin contar el propio ítem (al editarlo). */
    @Query("select count(i) > 0 from ItemCompraEntity i where i.logisticaEvento.eventoId = :eventoId "
            + "and lower(trim(i.nombreItem)) = lower(:nombre) and i.id <> :itemId")
    boolean existeOtroConNombreEnEvento(@Param("eventoId") UUID eventoId, @Param("nombre") String nombre,
                                        @Param("itemId") UUID itemId);
}