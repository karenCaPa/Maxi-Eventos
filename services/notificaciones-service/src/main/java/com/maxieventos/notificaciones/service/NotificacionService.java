package com.maxieventos.notificaciones.service;

import com.maxieventos.notificaciones.dto.NotificacionResponse;
import com.maxieventos.notificaciones.dto.PaginaResponse;
import com.maxieventos.notificaciones.repository.NotificacionRepository;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionService {
    private final NotificacionRepository repository;

    public NotificacionService(NotificacionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<NotificacionResponse> listar(UUID eventoId, Pageable pageable) {
        return PaginaResponse.de(eventoId == null ? repository.findAll(pageable)
                : repository.findAllByEventoId(eventoId, pageable), NotificacionResponse::de);
    }
}
