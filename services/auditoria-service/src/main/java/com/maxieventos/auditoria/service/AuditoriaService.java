package com.maxieventos.auditoria.service;

import com.maxieventos.auditoria.dto.AuditoriaEventoResponse;
import com.maxieventos.auditoria.dto.PaginaResponse;
import com.maxieventos.auditoria.entity.TipoMensaje;
import com.maxieventos.auditoria.repository.AuditoriaEventoRepository;
import com.maxieventos.auditoria.repository.AuditoriaFiltros;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {
    private final AuditoriaEventoRepository repository;

    public AuditoriaService(AuditoriaEventoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AuditoriaEventoResponse> listar(UUID eventoId, TipoMensaje tipo, Pageable pageable) {
        return PaginaResponse.de(repository.findAll(AuditoriaFiltros.de(eventoId, tipo), pageable),
                AuditoriaEventoResponse::de);
    }
}
