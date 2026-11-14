package com.maxieventos.eventos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita la tarea periódica de reenvío del outbox ({@code kafka/ReenvioOutbox}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
