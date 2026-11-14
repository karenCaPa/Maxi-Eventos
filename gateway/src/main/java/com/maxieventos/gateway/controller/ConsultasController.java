package com.maxieventos.gateway.controller;

import static com.maxieventos.gateway.config.TokenInterceptor.AUTORIZACION;

import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.Fecha;
import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.dto.ServicioCatalogo;
import com.maxieventos.gateway.service.remoto.ClientesClient;
import com.maxieventos.gateway.service.remoto.EventosClient;
import com.maxieventos.gateway.service.remoto.FinanzasClient;
import com.maxieventos.gateway.service.remoto.LugaresClient;
import com.maxieventos.gateway.service.remoto.ProveedoresClient;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Queries raíz. Cada una es una sola llamada REST; las relaciones de Evento las resuelve {@link EventoCamposController}. */
@Controller
public class ConsultasController {
    private final EventosClient eventos;
    private final ClientesClient clientes;
    private final FinanzasClient finanzas;
    private final LugaresClient lugares;
    private final ProveedoresClient proveedores;

    public ConsultasController(EventosClient eventos, ClientesClient clientes, FinanzasClient finanzas, LugaresClient lugares,
                               ProveedoresClient proveedores) {
        this.eventos = eventos;
        this.clientes = clientes;
        this.finanzas = finanzas;
        this.lugares = lugares;
        this.proveedores = proveedores;
    }

    @QueryMapping
    public Evento evento(@Argument UUID id, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return eventos.obtener(autorizacion, id);
    }

    @QueryMapping
    public Pagina<Evento> eventos(@Argument String estado, @Argument String tipo, @Argument LocalDate desde,
                                  @Argument LocalDate hasta, @Argument int page, @Argument int size, @Argument String sort,
                                  @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return eventos.listar(autorizacion, estado, tipo, desde, hasta, page, size, sort);
    }

    @QueryMapping
    public List<Fecha> fechasDisponibles(@Argument LocalDate desde, @Argument LocalDate hasta,
                                         @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return eventos.fechasDisponibles(autorizacion, desde, hasta);
    }

    @QueryMapping
    public Cliente cliente(@Argument UUID id, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return clientes.obtener(autorizacion, id);
    }

    @QueryMapping
    public Pagina<Cliente> clientes(@Argument String nombre, @Argument int page, @Argument int size, @Argument String sort,
                                    @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return clientes.listar(autorizacion, nombre, page, size, sort);
    }

    @QueryMapping
    public List<ServicioCatalogo> catalogoServiciosAdicionales(
            @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return finanzas.catalogo(autorizacion);
    }

    @QueryMapping
    public Pagina<Lugar> lugares(@Argument String nombre, @Argument String tipo, @Argument String estado, @Argument int page,
                                 @Argument int size, @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return lugares.listar(autorizacion, nombre, tipo, estado, page, size);
    }

    @QueryMapping
    public Pagina<Proveedor> proveedores(@Argument String categoria, @Argument Boolean activo, @Argument int page,
                                         @Argument int size,
                                         @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return proveedores.listar(autorizacion, categoria, activo, page, size);
    }
}
