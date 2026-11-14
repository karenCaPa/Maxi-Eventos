package com.maxieventos.gateway.controller;

import static com.maxieventos.gateway.config.TokenInterceptor.AUTORIZACION;
import static com.maxieventos.gateway.config.TokenInterceptor.ROL;

import com.maxieventos.gateway.security.Permisos;
import com.maxieventos.gateway.dto.Balance;
import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.Contrato;
import com.maxieventos.gateway.dto.Cotizacion;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.Logistica;
import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.Notificacion;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Pago;
import com.maxieventos.gateway.dto.ProveedorAsignado;
import com.maxieventos.gateway.dto.RegistroAuditoria;
import com.maxieventos.gateway.dto.ServicioAdicional;
import com.maxieventos.gateway.service.remoto.AuditoriaClient;
import com.maxieventos.gateway.service.remoto.ClientesClient;
import com.maxieventos.gateway.service.remoto.ContratosClient;
import com.maxieventos.gateway.service.remoto.FinanzasClient;
import com.maxieventos.gateway.service.remoto.LogisticaClient;
import com.maxieventos.gateway.service.remoto.LugaresClient;
import com.maxieventos.gateway.service.remoto.NotificacionesClient;
import com.maxieventos.gateway.service.remoto.ProveedoresClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Relaciones de {@code Evento}. Cada campo es un resolver propio: graphql-java solo lo ejecuta si la query lo pide, así
 * que solo se llama al servicio de los campos solicitados (una query que pide {@code cliente} no llama a Finanzas ni a
 * Contratos).
 * <p>
 * {@code cliente} y {@code lugar} usan {@link BatchMapping}: en un listado agrupa los eventos de la página y hace una
 * llamada por cliente/lugar DISTINTO (no una por evento). Los demás campos son propios de cada evento (no hay nada que
 * agrupar) y los servicios no tienen endpoints por lotes: siguen siendo una llamada por evento (ver docs/graphql.md, N+1).
 */
@Controller
@SchemaMapping(typeName = "Evento")
public class EventoCamposController {
    private final ClientesClient clientes;
    private final LugaresClient lugares;
    private final FinanzasClient finanzas;
    private final ContratosClient contratos;
    private final ProveedoresClient proveedores;
    private final LogisticaClient logistica;
    private final NotificacionesClient notificaciones;
    private final AuditoriaClient auditoria;

    public EventoCamposController(ClientesClient clientes, LugaresClient lugares, FinanzasClient finanzas,
                                  ContratosClient contratos, ProveedoresClient proveedores, LogisticaClient logistica,
                                  NotificacionesClient notificaciones, AuditoriaClient auditoria) {
        this.clientes = clientes;
        this.lugares = lugares;
        this.finanzas = finanzas;
        this.contratos = contratos;
        this.proveedores = proveedores;
        this.logistica = logistica;
        this.notificaciones = notificaciones;
        this.auditoria = auditoria;
    }

    @BatchMapping(typeName = "Evento")
    public Map<Evento, Cliente> cliente(List<Evento> eventos,
                                        @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return porIdDistinto(eventos, Evento::clienteId, id -> clientes.obtenerOpcional(autorizacion, id));
    }

    /** Sin {@code lugarId} no se llama a Lugares. */
    @BatchMapping(typeName = "Evento")
    public Map<Evento, Lugar> lugar(List<Evento> eventos,
                                    @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return porIdDistinto(eventos, Evento::lugarId, id -> lugares.obtenerOpcional(autorizacion, id));
    }

    @SchemaMapping
    public Cotizacion cotizacion(Evento evento, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.cotizacion(autorizacion, evento.id());
    }

    @SchemaMapping
    public Balance balance(Evento evento, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.balance(autorizacion, evento.id());
    }

    @SchemaMapping
    public Pagina<Pago> pagos(Evento evento, @Argument int page, @Argument int size,
                              @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.pagos(autorizacion, evento.id(), page, size);
    }

    @SchemaMapping
    public List<ServicioAdicional> serviciosAdicionales(Evento evento,
                                                        @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.adicionales(autorizacion, evento.id());
    }

    @SchemaMapping
    public Contrato contrato(Evento evento, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return contratos.contrato(autorizacion, evento.id());
    }

    @SchemaMapping
    public Pagina<ProveedorAsignado> proveedores(Evento evento, @Argument int page, @Argument int size,
                                                 @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return proveedores.asignaciones(autorizacion, evento.id(), page, size);
    }

    @SchemaMapping
    public Logistica logistica(Evento evento, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return logistica.requerimientos(autorizacion, evento.id());
    }

    @SchemaMapping
    public Pagina<Notificacion> notificaciones(Evento evento, @Argument int page, @Argument int size,
                                               @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return notificaciones.deEvento(autorizacion, evento.id(), page, size);
    }

    /** Fase 11D: solo ADMINISTRADOR (con otro rol: FORBIDDEN en este campo sin llamar a Auditoría; el resto de la ficha llega). */
    @SchemaMapping
    public Pagina<RegistroAuditoria> auditoria(Evento evento, @Argument String tipo, @Argument int page, @Argument int size,
                                               @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
                                               @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "Evento.auditoria");
        return auditoria.deEvento(autorizacion, evento.id(), tipo, page, size);
    }

    /** Una llamada por id distinto (sin ids nulos); los eventos sin dato quedan fuera del mapa (= null en la respuesta). */
    static <T> Map<Evento, T> porIdDistinto(List<Evento> eventos, Function<Evento, UUID> id, Function<UUID, T> obtener) {
        Map<UUID, T> porId = new LinkedHashMap<>();
        eventos.stream().map(id).filter(Objects::nonNull).distinct().forEach(clave -> porId.put(clave, obtener.apply(clave)));
        Map<Evento, T> resultado = new LinkedHashMap<>();
        for (Evento evento : eventos) {
            T valor = evento == null || id.apply(evento) == null ? null : porId.get(id.apply(evento));
            if (valor != null) {
                resultado.put(evento, valor);
            }
        }
        return resultado;
    }
}
