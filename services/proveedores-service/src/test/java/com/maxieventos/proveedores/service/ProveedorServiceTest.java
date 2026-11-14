package com.maxieventos.proveedores.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.proveedores.dto.EventoProveedorRequest;
import com.maxieventos.proveedores.dto.EventoProveedorResponseDTO;
import com.maxieventos.proveedores.dto.PaginaResponse;
import com.maxieventos.proveedores.dto.ProveedorRequest;
import com.maxieventos.proveedores.dto.ProveedorResponseDTO;
import com.maxieventos.proveedores.entity.EstadoAsignacion;
import com.maxieventos.proveedores.entity.EventoProveedor;
import com.maxieventos.proveedores.entity.Proveedor;
import com.maxieventos.proveedores.exception.RecursoNoEncontradoException;
import com.maxieventos.proveedores.exception.ReglaNegocioException;
import com.maxieventos.proveedores.exception.ServicioNoDisponibleException;
import com.maxieventos.proveedores.repository.EventoProveedorRepository;
import com.maxieventos.proveedores.repository.ProveedorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProveedorServiceTest {

    private static final String TOKEN = "Bearer token-de-prueba";
    private static final Pageable PAGINA = PageRequest.of(0, 10);

    @Mock
    private ProveedorRepository proveedorRepository;
    @Mock
    private EventoProveedorRepository asignacionRepository;
    @Mock
    private EventoServiceClient eventoServiceClient;

    @InjectMocks
    private ProveedorService service;

    // ------------------------------------------------------------------ duplicado por nombre

    @Test
    void crearConNombreRepetidoDa409() {
        when(proveedorRepository.existeConNombre("Flores del Huila")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(new ProveedorRequest("  Flores del Huila ", "FLORISTERIA", "3001234567")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Ya existe un proveedor con el nombre Flores del Huila");
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    void crearConNombreNuevoGuardaElNombreRecortado() {
        when(proveedorRepository.existeConNombre("Sonido Pro")).thenReturn(false);
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        ProveedorResponseDTO creado = service.crear(new ProveedorRequest(" Sonido Pro ", "SONIDO", "3001234567"));

        assertThat(creado.nombre()).isEqualTo("Sonido Pro");
        assertThat(creado.activo()).isTrue();
    }

    @Test
    void actualizarConElNombreDeOtroProveedorDa409() {
        UUID id = UUID.randomUUID();
        when(proveedorRepository.findById(id)).thenReturn(Optional.of(new Proveedor("Sonido Pro", "SONIDO", "300")));
        when(proveedorRepository.existeOtroConNombre("flores del huila", id)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(id, new ProveedorRequest("flores del huila", "SONIDO", "300")))
                .isInstanceOf(ReglaNegocioException.class);
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    void actualizarConservandoSuPropioNombreEsValido() {
        UUID id = UUID.randomUUID();
        Proveedor proveedor = new Proveedor("Sonido Pro", "SONIDO", "300");
        when(proveedorRepository.findById(id)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.existeOtroConNombre("Sonido Pro", id)).thenReturn(false);
        when(proveedorRepository.save(proveedor)).thenReturn(proveedor);

        assertThat(service.actualizar(id, new ProveedorRequest("Sonido Pro", "SONIDO", "311")).contacto()).isEqualTo("311");
    }

    // ------------------------------------------------------------------ eliminar

    @Test
    void eliminarProveedorSinAsignaciones() {
        UUID id = UUID.randomUUID();
        Proveedor proveedor = new Proveedor("Sonido Pro", "SONIDO", "300");
        when(proveedorRepository.findById(id)).thenReturn(Optional.of(proveedor));
        when(asignacionRepository.existsByProveedorId(id)).thenReturn(false);

        service.eliminar(id);

        verify(proveedorRepository).delete(proveedor);
    }

    @Test
    void eliminarProveedorConAsignacionesDa409() {
        UUID id = UUID.randomUUID();
        when(proveedorRepository.findById(id)).thenReturn(Optional.of(new Proveedor("Sonido Pro", "SONIDO", "300")));
        when(asignacionRepository.existsByProveedorId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(id))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Inactívalo");
        verify(proveedorRepository, never()).delete(any(Proveedor.class));
    }

    @Test
    void eliminarProveedorInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(proveedorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(id)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------ asignaciones de un evento

    @Test
    void asignacionesDeUnEventoInexistenteDa404() {
        UUID eventoId = UUID.randomUUID();
        doThrow(new RecursoNoEncontradoException("No existe un evento con id " + eventoId))
                .when(eventoServiceClient).validarEvento(eventoId, TOKEN);

        assertThatThrownBy(() -> service.listarAsignaciones(eventoId, PAGINA, TOKEN))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(asignacionRepository);
    }

    @Test
    void eventoSinAsignacionesDevuelvePaginaVacia() {
        UUID eventoId = UUID.randomUUID();
        when(asignacionRepository.findByEventoId(eventoId, PAGINA)).thenReturn(Page.empty(PAGINA));

        PaginaResponse<EventoProveedorResponseDTO> pagina = service.listarAsignaciones(eventoId, PAGINA, TOKEN);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }

    @Test
    void asignacionesConEventosCaidoDa503() {
        UUID eventoId = UUID.randomUUID();
        doThrow(new ServicioNoDisponibleException("El servicio de eventos no está disponible", null))
                .when(eventoServiceClient).validarEvento(eventoId, TOKEN);

        assertThatThrownBy(() -> service.listarAsignaciones(eventoId, PAGINA, TOKEN))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void asignacionesDeUnEventoConDatos() {
        UUID eventoId = UUID.randomUUID();
        EventoProveedor asignacion = new EventoProveedor(eventoId, UUID.randomUUID(), "Sonido", new BigDecimal("500000"),
                EstadoAsignacion.CONFIRMADO);
        when(asignacionRepository.findByEventoId(eventoId, PAGINA)).thenReturn(new PageImpl<>(List.of(asignacion), PAGINA, 1));

        PaginaResponse<EventoProveedorResponseDTO> pagina = service.listarAsignaciones(eventoId, PAGINA, TOKEN);

        assertThat(pagina.contenido()).singleElement()
                .satisfies(a -> assertThat(a.estado()).isEqualTo(EstadoAsignacion.CONFIRMADO));
    }

    // ------------------------------------------------------------------ asignar

    @Test
    void asignarSinEstadoQuedaPendiente() {
        UUID eventoId = UUID.randomUUID();
        UUID proveedorId = UUID.randomUUID();
        when(proveedorRepository.findById(proveedorId)).thenReturn(Optional.of(new Proveedor("Sonido Pro", "SONIDO", "300")));
        when(asignacionRepository.saveAndFlush(any(EventoProveedor.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        EventoProveedorResponseDTO asignacion = service.asignar(
                new EventoProveedorRequest(eventoId, proveedorId, "Sonido", new BigDecimal("500000"), null), TOKEN);

        assertThat(asignacion.estado()).isEqualTo(EstadoAsignacion.PENDIENTE);
        verify(eventoServiceClient).validarEvento(eventoId, TOKEN);
    }

    @Test
    void asignarProveedorInactivoDa409() {
        UUID proveedorId = UUID.randomUUID();
        Proveedor inactivo = new Proveedor("Sonido Pro", "SONIDO", "300");
        inactivo.setActivo(false);
        when(proveedorRepository.findById(proveedorId)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> service.asignar(new EventoProveedorRequest(UUID.randomUUID(), proveedorId, "Sonido",
                new BigDecimal("500000"), EstadoAsignacion.PENDIENTE), TOKEN))
                .isInstanceOf(ReglaNegocioException.class);
        verify(asignacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void asignarProveedorYaAsignadoAlEventoParaElMismoServicioDa409ConMensajeClaro() {
        UUID eventoId = UUID.randomUUID();
        UUID proveedorId = UUID.randomUUID();
        when(proveedorRepository.findById(proveedorId)).thenReturn(Optional.of(new Proveedor("Sonido Pro", "SONIDO", "300")));
        when(asignacionRepository.existsByEventoIdAndProveedorIdAndServicioAsignado(eventoId, proveedorId, "Sonido"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.asignar(new EventoProveedorRequest(eventoId, proveedorId, "Sonido",
                new BigDecimal("500000"), null), TOKEN))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El proveedor «Sonido Pro» ya está asignado a este evento para el servicio «Sonido»");
        verify(asignacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void asignarElMismoProveedorAlEventoParaOtroServicioEsValido() {
        UUID eventoId = UUID.randomUUID();
        UUID proveedorId = UUID.randomUUID();
        when(proveedorRepository.findById(proveedorId)).thenReturn(Optional.of(new Proveedor("Sonido Pro", "SONIDO", "300")));
        when(asignacionRepository.existsByEventoIdAndProveedorIdAndServicioAsignado(eventoId, proveedorId, "Iluminación"))
                .thenReturn(false);
        when(asignacionRepository.saveAndFlush(any(EventoProveedor.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        EventoProveedorResponseDTO asignacion = service.asignar(new EventoProveedorRequest(eventoId, proveedorId,
                "Iluminación", new BigDecimal("300000"), null), TOKEN);

        assertThat(asignacion.servicioAsignado()).isEqualTo("Iluminación");
        verify(asignacionRepository).saveAndFlush(any(EventoProveedor.class));
    }

    // ------------------------------------------------------------------ cambiarEstadoAsignacion

    @Test
    void confirmarUnaAsignacionPendienteLaPasaAConfirmado() {
        UUID id = UUID.randomUUID();
        EventoProveedor asignacion = new EventoProveedor(UUID.randomUUID(), UUID.randomUUID(), "Sonido",
                new BigDecimal("500000.00"), EstadoAsignacion.PENDIENTE);
        when(asignacionRepository.findById(id)).thenReturn(Optional.of(asignacion));
        when(asignacionRepository.save(asignacion)).thenReturn(asignacion);

        EventoProveedorResponseDTO resultado = service.cambiarEstadoAsignacion(id, EstadoAsignacion.CONFIRMADO);

        assertThat(resultado.estado()).isEqualTo(EstadoAsignacion.CONFIRMADO);
        verify(asignacionRepository).save(asignacion);
    }

    @Test
    void confirmarUnaAsignacionInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(asignacionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstadoAsignacion(id, EstadoAsignacion.CONFIRMADO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void confirmarUnaAsignacionYaConfirmadaDa409() {
        UUID id = UUID.randomUUID();
        EventoProveedor asignacion = new EventoProveedor(UUID.randomUUID(), UUID.randomUUID(), "Sonido",
                new BigDecimal("500000.00"), EstadoAsignacion.CONFIRMADO);
        when(asignacionRepository.findById(id)).thenReturn(Optional.of(asignacion));

        assertThatThrownBy(() -> service.cambiarEstadoAsignacion(id, EstadoAsignacion.CONFIRMADO))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Solo se puede confirmar una asignación pendiente; esta ya está confirmada.");
        verify(asignacionRepository, never()).save(any(EventoProveedor.class));
    }

    @Test
    void pedirUnEstadoDistintoDeConfirmadoDa409() {
        UUID id = UUID.randomUUID();
        EventoProveedor asignacion = new EventoProveedor(UUID.randomUUID(), UUID.randomUUID(), "Sonido",
                new BigDecimal("500000.00"), EstadoAsignacion.PENDIENTE);
        when(asignacionRepository.findById(id)).thenReturn(Optional.of(asignacion));

        assertThatThrownBy(() -> service.cambiarEstadoAsignacion(id, EstadoAsignacion.CANCELADO))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Una asignación solo se puede pasar de pendiente a confirmada; se pidió dejarla cancelada.");
        verify(asignacionRepository, never()).save(any(EventoProveedor.class));
    }
}
