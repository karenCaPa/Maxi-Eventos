package com.maxieventos.gateway.controller;

import static com.maxieventos.gateway.config.TokenInterceptor.AUTORIZACION;
import static com.maxieventos.gateway.config.TokenInterceptor.ROL;

import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.LugarInput;
import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.dto.ProveedorInput;
import com.maxieventos.gateway.security.Permisos;
import com.maxieventos.gateway.service.remoto.LugaresClient;
import com.maxieventos.gateway.service.remoto.ProveedoresClient;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * Mutations de los directorios de lugares y proveedores (Fase 10B3, registro desde la interfaz). Igual que las demás
 * mutations, cada una DELEGA en una sola llamada REST sin lógica de negocio: las validaciones (campos obligatorios,
 * capacidad &gt; 0, nombre de proveedor repetido…) las aplica el servicio y sus errores llegan como BAD_REQUEST con
 * {@code errores[]}, NOT_FOUND o CONFLICT.
 * <p>
 * Fase 11D: las seis son de ADMINISTRADOR. Con otro rol (o un token sin rol) responden FORBIDDEN sin llamar al servicio;
 * Lugares y Proveedores lo vuelven a comprobar (403) si se les llama directamente.
 */
@Controller
public class DirectoriosController {
    private final LugaresClient lugares;
    private final ProveedoresClient proveedores;

    public DirectoriosController(LugaresClient lugares, ProveedoresClient proveedores) {
        this.lugares = lugares;
        this.proveedores = proveedores;
    }

    @MutationMapping
    public Lugar crearLugar(@Argument LugarInput input, @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
                            @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "crearLugar");
        return lugares.crear(autorizacion, input);
    }

    @MutationMapping
    public Lugar actualizarLugar(@Argument UUID id, @Argument LugarInput input,
                                 @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
                                 @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "actualizarLugar");
        return lugares.actualizar(autorizacion, id, input);
    }

    @MutationMapping
    public Lugar cambiarEstadoLugar(@Argument UUID id, @Argument String estado,
                                    @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
                                    @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "cambiarEstadoLugar");
        return lugares.cambiarEstado(autorizacion, id, estado);
    }

    @MutationMapping
    public Proveedor crearProveedor(@Argument ProveedorInput input,
                                    @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
                                    @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "crearProveedor");
        return proveedores.crear(autorizacion, input);
    }

    @MutationMapping
    public Proveedor actualizarProveedor(@Argument UUID id, @Argument ProveedorInput input,
                                         @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
                                         @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "actualizarProveedor");
        return proveedores.actualizar(autorizacion, id, input);
    }

    @MutationMapping
    public Proveedor cambiarEstadoProveedor(@Argument UUID id, @Argument boolean activo,
                                            @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
                                            @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "cambiarEstadoProveedor");
        return proveedores.cambiarEstado(autorizacion, id, activo);
    }
}
