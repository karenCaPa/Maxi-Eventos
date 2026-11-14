package com.maxieventos.auditoria.repository;

import com.maxieventos.auditoria.entity.AuditoriaEvento;
import com.maxieventos.auditoria.entity.TipoMensaje;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filtros opcionales del listado de auditoría (mismo patrón que {@code EventoFiltros} de eventos-service). */
public final class AuditoriaFiltros {

    private AuditoriaFiltros() {
    }

    public static Specification<AuditoriaEvento> de(UUID eventoId, TipoMensaje tipo) {
        Specification<AuditoriaEvento> filtro = Specification.where(null);
        if (eventoId != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("eventoId"), eventoId));
        }
        if (tipo != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("tipo"), tipo.name()));
        }
        return filtro;
    }
}
