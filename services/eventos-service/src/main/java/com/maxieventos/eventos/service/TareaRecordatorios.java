package com.maxieventos.eventos.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La revisión diaria de recordatorios (Fase 11E). Horario: {@code RECORDATORIOS_CRON} (por defecto todos los días a las
 * 7:00, "0 0 7 * * *") en la zona {@code ZONA_HORARIA}. Con "-" Spring no la programa: para la sustentación conviene
 * desactivarla y ejecutar la revisión a mano ({@code POST /api/v1/recordatorios/ejecutar}, mutation ejecutarRecordatorios),
 * así el recordatorio no sale solo a las 7:00 antes de mostrarlo.
 */
@Component
public class TareaRecordatorios {

    private final RecordatoriosService servicio;

    public TareaRecordatorios(RecordatoriosService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(cron = "${maxi-eventos.recordatorios.cron}", zone = "${maxi-eventos.zona-horaria}")
    public void revisar() {
        servicio.ejecutar("PROGRAMADA");
    }
}
