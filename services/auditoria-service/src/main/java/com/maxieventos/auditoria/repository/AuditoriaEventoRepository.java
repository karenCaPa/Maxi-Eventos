package com.maxieventos.auditoria.repository;

import com.maxieventos.auditoria.entity.AuditoriaEvento;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, UUID>,
        JpaSpecificationExecutor<AuditoriaEvento> {
}
