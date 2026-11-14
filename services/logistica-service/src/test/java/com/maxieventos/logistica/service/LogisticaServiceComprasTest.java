package com.maxieventos.logistica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.logistica.dto.ActualizarItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraResponseDTO;
import com.maxieventos.logistica.dto.PaginaResponse;
import com.maxieventos.logistica.entity.ItemCompraEntity;
import com.maxieventos.logistica.entity.LogisticaEvento;
import com.maxieventos.logistica.entity.OrigenItemCompra;
import com.maxieventos.logistica.exception.RecursoNoEncontradoException;
import com.maxieventos.logistica.exception.ReglaNegocioException;
import com.maxieventos.logistica.repository.ItemCompraRepository;
import com.maxieventos.logistica.repository.LogisticaEventoRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LogisticaServiceComprasTest {

    private static final Pageable PAGINA = PageRequest.of(0, 20);

    @Mock
    private LogisticaEventoRepository logisticaRepository;
    @Mock
    private ItemCompraRepository comprasRepository;

    @InjectMocks
    private LogisticaService service;

    @Test
    void marcarCompradoDevuelveLaPaginaActualizada() {
        UUID eventoId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ItemCompraEntity hielo = new ItemCompraEntity("Hielo", "10 bolsas");
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 8, 4, 2)));
        when(comprasRepository.findByIdAndLogisticaEventoEventoId(itemId, eventoId)).thenReturn(Optional.of(hielo));
        when(comprasRepository.findAllByLogisticaEventoEventoId(eventoId, PAGINA))
                .thenReturn(new PageImpl<>(List.of(hielo), PAGINA, 1));

        PaginaResponse<ItemCompraResponseDTO> pagina = service.actualizarCompras(eventoId,
                List.of(new ActualizarItemCompraRequest(itemId, true)), PAGINA);

        assertThat(hielo.isComprado()).isTrue();
        assertThat(pagina.contenido()).singleElement().satisfies(item -> assertThat(item.comprado()).isTrue());
        verify(comprasRepository).flush();
    }

    @Test
    void itemDeOtroEventoDa404() {
        UUID eventoId = UUID.randomUUID();
        UUID itemAjeno = UUID.randomUUID();
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 8, 4, 2)));
        when(comprasRepository.findByIdAndLogisticaEventoEventoId(itemAjeno, eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarCompras(eventoId,
                List.of(new ActualizarItemCompraRequest(itemAjeno, true)), PAGINA))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining(itemAjeno.toString());
        verify(comprasRepository, never()).findAllByLogisticaEventoEventoId(any(), any());
    }

    // ------------------------------------------------------------------ 10B3: ítems manuales

    @Test
    void agregarCreaUnItemManualSinMarcarConLosTextosRecortados() {
        UUID eventoId = UUID.randomUUID();
        LogisticaEvento evento = new LogisticaEvento(eventoId, 6, 3, 1);
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(comprasRepository.existeNombreEnEvento(eventoId, "Recuerdos para invitados")).thenReturn(false);

        ItemCompraResponseDTO item = service.agregarItem(eventoId,
                new ItemCompraRequest("  Recuerdos para invitados ", " 100 unidades ", "   "));

        assertThat(item.nombreItem()).isEqualTo("Recuerdos para invitados");
        assertThat(item.cantidadEstimada()).isEqualTo("100 unidades");
        assertThat(item.origen()).isEqualTo(OrigenItemCompra.MANUAL);
        assertThat(item.comprado()).isFalse();
        assertThat(item.nota()).as("una nota en blanco se guarda vacía").isNull();
        assertThat(evento.getCompras()).singleElement().satisfies(i -> assertThat(i.esManual()).isTrue());
        verify(comprasRepository).saveAndFlush(any(ItemCompraEntity.class));
    }

    @Test
    void agregarUnNombreRepetidoDa409SinGuardar() {
        UUID eventoId = UUID.randomUUID();
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 6, 3, 1)));
        when(comprasRepository.existeNombreEnEvento(eventoId, "hielo")).thenReturn(true);

        assertThatThrownBy(() -> service.agregarItem(eventoId, new ItemCompraRequest(" hielo ", "2 bolsas", null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya hay un ítem «hielo» en la lista de compras de este evento.");
        verify(comprasRepository, never()).saveAndFlush(any());
    }

    @Test
    void agregarSinLogisticaDa404() {
        UUID eventoId = UUID.randomUUID();
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agregarItem(eventoId, new ItemCompraRequest("Velas", "20", null)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe logística para el evento " + eventoId);
    }

    @Test
    void editarConservaElOrigenYLaMarcaDeComprado() {
        UUID eventoId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ItemCompraEntity hielo = new ItemCompraEntity("Hielo", "11 bolsas");
        hielo.setComprado(true);
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 6, 3, 1)));
        when(comprasRepository.findByIdAndLogisticaEventoEventoId(itemId, eventoId)).thenReturn(Optional.of(hielo));
        when(comprasRepository.existeOtroConNombreEnEvento(eventoId, "Hielo en cubos", itemId)).thenReturn(false);

        ItemCompraResponseDTO editado = service.editarItem(eventoId, itemId,
                new ItemCompraRequest("Hielo en cubos", "15 bolsas", "Pedir al proveedor de siempre"));

        assertThat(editado.nombreItem()).isEqualTo("Hielo en cubos");
        assertThat(editado.cantidadEstimada()).isEqualTo("15 bolsas");
        assertThat(editado.nota()).isEqualTo("Pedir al proveedor de siempre");
        assertThat(editado.comprado()).isTrue();
        assertThat(editado.origen()).isEqualTo(OrigenItemCompra.CALCULADO);
    }

    @Test
    void editarConElNombreDeOtroItemDa409() {
        UUID eventoId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 6, 3, 1)));
        when(comprasRepository.findByIdAndLogisticaEventoEventoId(itemId, eventoId))
                .thenReturn(Optional.of(ItemCompraEntity.manual("Velas", "20", null)));
        when(comprasRepository.existeOtroConNombreEnEvento(eventoId, "BEBIDAS", itemId)).thenReturn(true);

        assertThatThrownBy(() -> service.editarItem(eventoId, itemId, new ItemCompraRequest("BEBIDAS", "1", null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("«BEBIDAS»");
    }

    @Test
    void quitarUnItemManualLoBorra() {
        UUID eventoId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ItemCompraEntity recuerdos = ItemCompraEntity.manual("Recuerdos para invitados", "100", null);
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 6, 3, 1)));
        when(comprasRepository.findByIdAndLogisticaEventoEventoId(itemId, eventoId)).thenReturn(Optional.of(recuerdos));

        service.quitarItem(eventoId, itemId);

        verify(comprasRepository).delete(recuerdos);
    }

    @Test
    void quitarUnItemCalculadoDa409YNoBorraNada() {
        UUID eventoId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 6, 3, 1)));
        when(comprasRepository.findByIdAndLogisticaEventoEventoId(itemId, eventoId))
                .thenReturn(Optional.of(new ItemCompraEntity("Hielo", "11 bolsas")));

        assertThatThrownBy(() -> service.quitarItem(eventoId, itemId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Los ítems calculados por el sistema no se pueden quitar; puedes editarlos o dejarlos sin marcar.");
        verify(comprasRepository, never()).delete(any());
    }

    @Test
    void quitarOEditarUnItemDeOtroEventoDa404() {
        UUID eventoId = UUID.randomUUID();
        UUID ajeno = UUID.randomUUID();
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(new LogisticaEvento(eventoId, 6, 3, 1)));
        when(comprasRepository.findByIdAndLogisticaEventoEventoId(ajeno, eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quitarItem(eventoId, ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe el ítem de compra " + ajeno + " en el evento " + eventoId);
        assertThatThrownBy(() -> service.editarItem(eventoId, ajeno, new ItemCompraRequest("Velas", "20", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(comprasRepository, never()).delete(any());
    }

    @Test
    void eventoSinLogisticaDa404() {
        UUID eventoId = UUID.randomUUID();
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarCompras(eventoId,
                List.of(new ActualizarItemCompraRequest(UUID.randomUUID(), true)), PAGINA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
