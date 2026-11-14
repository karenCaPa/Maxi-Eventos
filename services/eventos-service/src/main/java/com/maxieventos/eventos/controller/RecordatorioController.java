package com.maxieventos.eventos.controller;

import com.maxieventos.eventos.dto.ResultadoRecordatoriosResponse;
import com.maxieventos.eventos.service.RecordatoriosService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ejecución manual de la revisión de recordatorios (Fase 11E, para la demostración). Solo ADMINISTRADOR
 * ({@code SecurityConfig}: el asistente recibe 403). Hace lo mismo que la tarea diaria: envía cada recordatorio a lo sumo
 * una vez por evento y umbral, así que repetirla no duplica nada (los ya enviados salen como YA_ENVIADO).
 */
@RestController
@RequestMapping("/api/v1/recordatorios")
public class RecordatorioController {
    private final RecordatoriosService servicio;

    public RecordatorioController(RecordatoriosService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/ejecutar")
    public ResultadoRecordatoriosResponse ejecutar() {
        return servicio.ejecutar("MANUAL");
    }
}
