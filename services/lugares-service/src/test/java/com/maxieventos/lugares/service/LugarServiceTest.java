package com.maxieventos.lugares.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.lugares.dto.LugarRequest;
import com.maxieventos.lugares.dto.LugarResponseDTO;
import com.maxieventos.lugares.entity.EstadoLugar;
import com.maxieventos.lugares.entity.Lugar;
import com.maxieventos.lugares.exception.RecursoNoEncontradoException;
import com.maxieventos.lugares.repository.LugarRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LugarServiceTest {

    @Mock
    private LugarRepository repository;

    @InjectMocks
    private LugarService service;

    @Test
    void unLugarNuevoQuedaActivo() {
        when(repository.save(any(Lugar.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        LugarResponseDTO creado = service.crear(new LugarRequest("Salón Real", "Calle 1", "SALON", 150));

        assertThat(creado.estado()).isEqualTo(EstadoLugar.ACTIVO);
    }

    @Test
    void cambiarEstadoInactivaElLugar() {
        UUID id = UUID.randomUUID();
        Lugar lugar = new Lugar("Finca El Lago", "Vereda 2", "FINCA", 200);
        when(repository.findById(id)).thenReturn(Optional.of(lugar));
        when(repository.save(lugar)).thenReturn(lugar);

        LugarResponseDTO respuesta = service.cambiarEstado(id, EstadoLugar.INACTIVO);

        assertThat(respuesta.estado()).isEqualTo(EstadoLugar.INACTIVO);
        assertThat(lugar.getEstado()).isEqualTo(EstadoLugar.INACTIVO);
    }

    @Test
    void reactivarUnLugar() {
        UUID id = UUID.randomUUID();
        Lugar lugar = new Lugar("Finca El Lago", "Vereda 2", "FINCA", 200);
        lugar.cambiarEstado(EstadoLugar.INACTIVO);
        when(repository.findById(id)).thenReturn(Optional.of(lugar));
        when(repository.save(lugar)).thenReturn(lugar);

        assertThat(service.cambiarEstado(id, EstadoLugar.ACTIVO).estado()).isEqualTo(EstadoLugar.ACTIVO);
    }

    @Test
    void actualizarNoCambiaElEstado() {
        UUID id = UUID.randomUUID();
        Lugar lugar = new Lugar("Finca El Lago", "Vereda 2", "FINCA", 200);
        lugar.cambiarEstado(EstadoLugar.INACTIVO);
        when(repository.findById(id)).thenReturn(Optional.of(lugar));
        when(repository.save(lugar)).thenReturn(lugar);

        LugarResponseDTO respuesta = service.actualizar(id, new LugarRequest("Finca El Lago", "Vereda 3", "FINCA", 250));

        assertThat(respuesta.estado()).isEqualTo(EstadoLugar.INACTIVO);
        assertThat(respuesta.capacidad()).isEqualTo(250);
    }

    @Test
    void cambiarEstadoDeUnLugarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(id, EstadoLugar.INACTIVO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repository, never()).save(any());
    }
}
