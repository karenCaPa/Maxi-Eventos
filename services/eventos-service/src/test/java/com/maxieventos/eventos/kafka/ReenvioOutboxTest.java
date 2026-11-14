package com.maxieventos.eventos.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.eventos.repository.MensajeSalienteRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReenvioOutboxTest {

    @Mock
    private MensajeSalienteRepository repository;
    @Mock
    private OutboxKafka outbox;

    private ReenvioOutbox reenvio;

    @BeforeEach
    void crear() {
        reenvio = new ReenvioOutbox(repository, outbox, Duration.ofSeconds(30), Duration.ofMinutes(2), 50);
    }

    @Test
    void sinPendientesNoEnviaNada() {
        when(repository.candidatosAReenvio(any(), any(), any(Pageable.class))).thenReturn(List.of());

        reenvio.reenviarPendientes();

        verifyNoInteractions(outbox);
    }

    @Test
    void reenviaLosPendientesEnOrden() {
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(repository.candidatosAReenvio(any(), any(), any(Pageable.class))).thenReturn(List.of(primero, segundo));
        when(outbox.enviar(any(), any())).thenReturn(true);

        reenvio.reenviarPendientes();

        InOrder orden = inOrder(outbox);
        orden.verify(outbox).enviar(primero, OutboxKafka.REENVIO);
        orden.verify(outbox).enviar(segundo, OutboxKafka.REENVIO);
    }

    @Test
    void siKafkaNoRespondeSeDetieneElLote() {
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(repository.candidatosAReenvio(any(), any(), any(Pageable.class))).thenReturn(List.of(primero, segundo));
        when(outbox.enviar(primero, OutboxKafka.REENVIO)).thenReturn(false);

        reenvio.reenviarPendientes();

        verify(outbox, never()).enviar(segundo, OutboxKafka.REENVIO);
    }
}
