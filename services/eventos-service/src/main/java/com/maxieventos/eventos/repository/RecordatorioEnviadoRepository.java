package com.maxieventos.eventos.repository;

import com.maxieventos.eventos.entity.RecordatorioEnviado;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordatorioEnviadoRepository extends JpaRepository<RecordatorioEnviado, UUID> {

    boolean existsByEventoIdAndUmbralDias(UUID eventoId, int umbralDias);
}
