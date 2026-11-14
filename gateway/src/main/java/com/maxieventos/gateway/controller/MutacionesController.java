package com.maxieventos.gateway.controller;

import static com.maxieventos.gateway.config.TokenInterceptor.AUTORIZACION;

import com.maxieventos.gateway.dto.AsignarProveedorInput;
import com.maxieventos.gateway.dto.AuthPayload;
import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.ClienteInput;
import com.maxieventos.gateway.dto.Contrato;
import com.maxieventos.gateway.dto.Cotizacion;
import com.maxieventos.gateway.dto.CrearEventoInput;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.ItemCompra;
import com.maxieventos.gateway.dto.ItemCompraDatosInput;
import com.maxieventos.gateway.dto.ItemCompraInput;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Pago;
import com.maxieventos.gateway.dto.PagoInput;
import com.maxieventos.gateway.dto.ProveedorAsignado;
import com.maxieventos.gateway.dto.ServicioAdicional;
import com.maxieventos.gateway.service.remoto.AuthClient;
import com.maxieventos.gateway.service.remoto.ClientesClient;
import com.maxieventos.gateway.service.remoto.ContratosClient;
import com.maxieventos.gateway.service.remoto.EventosClient;
import com.maxieventos.gateway.service.remoto.FinanzasClient;
import com.maxieventos.gateway.service.remoto.LogisticaClient;
import com.maxieventos.gateway.service.remoto.ProveedoresClient;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * Mutations: cada una DELEGA en una sola llamada REST y devuelve lo que devuelve el servicio. No hay lógica de negocio en
 * el gateway: las validaciones y reglas (fecha libre, contrato firmado, depósito, saldo…) las aplica el servicio, y sus
 * errores llegan como errores GraphQL (BAD_REQUEST con {@code errores[]}, CONFLICT, NOT_FOUND, SERVICE_UNAVAILABLE…).
 * El resultado es un tipo del schema, así que se pueden pedir sus relaciones (p. ej. {@code confirmarEvento { cliente { … } }}).
 */
@Controller
public class MutacionesController {
    private final AuthClient auth;
    private final ClientesClient clientes;
    private final EventosClient eventos;
    private final FinanzasClient finanzas;
    private final ContratosClient contratos;
    private final ProveedoresClient proveedores;
    private final LogisticaClient logistica;

    public MutacionesController(AuthClient auth, ClientesClient clientes, EventosClient eventos, FinanzasClient finanzas,
                                ContratosClient contratos, ProveedoresClient proveedores, LogisticaClient logistica) {
        this.auth = auth;
        this.clientes = clientes;
        this.eventos = eventos;
        this.finanzas = finanzas;
        this.contratos = contratos;
        this.proveedores = proveedores;
        this.logistica = logistica;
    }

    @MutationMapping
    public AuthPayload login(@Argument String correo, @Argument String password) {
        return auth.login(correo, password);
    }

    @MutationMapping
    public Cliente crearCliente(@Argument ClienteInput input,
                                @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return clientes.crear(autorizacion, input);
    }

    @MutationMapping
    public Evento crearEvento(@Argument CrearEventoInput input,
                              @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return eventos.crear(autorizacion, input);
    }

    @MutationMapping
    public Cotizacion generarCotizacion(@Argument UUID eventoId,
                                        @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.generarCotizacion(autorizacion, eventoId);
    }

    @MutationMapping
    public Contrato crearContrato(@Argument UUID eventoId,
                                  @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return contratos.crear(autorizacion, eventoId);
    }

    @MutationMapping
    public Contrato firmarContrato(@Argument UUID eventoId,
                                   @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return contratos.firmar(autorizacion, eventoId);
    }

    @MutationMapping
    public Pago registrarPago(@Argument PagoInput input,
                              @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.registrarPago(autorizacion, input);
    }

    @MutationMapping
    public Evento confirmarEvento(@Argument UUID id, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return eventos.confirmar(autorizacion, id);
    }

    @MutationMapping
    public Evento cancelarEvento(@Argument UUID id, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return eventos.cancelar(autorizacion, id);
    }

    @MutationMapping
    public ServicioAdicional agregarServicioAdicional(@Argument UUID eventoId, @Argument String codigo, @Argument int cantidad,
                                                      @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.agregarAdicional(autorizacion, eventoId, codigo, cantidad);
    }

    @MutationMapping
    public Evento asignarLugar(@Argument UUID eventoId, @Argument UUID lugarId,
                               @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return eventos.asignarLugar(autorizacion, eventoId, lugarId);
    }

    @MutationMapping
    public ProveedorAsignado asignarProveedor(@Argument AsignarProveedorInput input,
                                              @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return proveedores.asignar(autorizacion, input);
    }

    @MutationMapping
    public ProveedorAsignado cambiarEstadoAsignacion(@Argument UUID id, @Argument String estado,
                                                     @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return proveedores.cambiarEstadoAsignacion(autorizacion, id, estado);
    }

    @MutationMapping
    public Pagina<ItemCompra> actualizarCompras(@Argument UUID eventoId, @Argument List<ItemCompraInput> items,
                                                @Argument int page, @Argument int size,
                                                @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return logistica.actualizarCompras(autorizacion, eventoId, items, page, size);
    }

    @MutationMapping
    public ItemCompra agregarItemCompra(@Argument UUID eventoId, @Argument ItemCompraDatosInput input,
                                        @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return logistica.agregarItem(autorizacion, eventoId, input);
    }

    @MutationMapping
    public ItemCompra editarItemCompra(@Argument UUID eventoId, @Argument UUID itemId, @Argument ItemCompraDatosInput input,
                                       @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return logistica.editarItem(autorizacion, eventoId, itemId, input);
    }

    /** Devuelve el id del ítem quitado (Logística responde 204 sin cuerpo). */
    @MutationMapping
    public UUID quitarItemCompra(@Argument UUID eventoId, @Argument UUID itemId,
                                 @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        logistica.quitarItem(autorizacion, eventoId, itemId);
        return itemId;
    }
}
