package com.maxieventos.gateway.controller;

import static com.maxieventos.gateway.config.TokenInterceptor.AUTORIZACION;

import com.maxieventos.gateway.dto.Balance;
import com.maxieventos.gateway.dto.Cotizacion;
import com.maxieventos.gateway.dto.ItemCompra;
import com.maxieventos.gateway.dto.Logistica;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.dto.ProveedorAsignado;
import com.maxieventos.gateway.service.remoto.FinanzasClient;
import com.maxieventos.gateway.service.remoto.LogisticaClient;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Campos de segundo nivel que también consultan a un servicio, solo si se piden. */
@Controller
public class CamposAnidadosController {
    private final FinanzasClient finanzas;
    private final LogisticaClient logistica;

    public CamposAnidadosController(FinanzasClient finanzas, LogisticaClient logistica) {
        this.finanzas = finanzas;
        this.logistica = logistica;
    }

    /** {@code Cotizacion.total}: total incluyendo los servicios adicionales (lo calcula Finanzas en el balance). */
    @SchemaMapping(typeName = "Cotizacion", field = "total")
    public BigDecimal totalConAdicionales(Cotizacion cotizacion,
                                          @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        Balance balance = finanzas.balance(autorizacion, cotizacion.eventoId());
        return balance == null ? null : balance.totalCotizado();
    }

    @SchemaMapping(typeName = "Logistica")
    public Pagina<ItemCompra> compras(Logistica requerimientos, @Argument int page, @Argument int size,
                                      @ContextValue(name = AUTORIZACION, required = false) String autorizacion) {
        return logistica.compras(autorizacion, requerimientos.eventoId(), page, size);
    }

    /**
     * {@code proveedor} y {@code categoria} comparten un DataLoader ({@link com.maxieventos.gateway.config.CargadoresConfig}):
     * una sola llamada por proveedor distinto aunque se pidan los dos campos.
     */
    @SchemaMapping(typeName = "ProveedorAsignado")
    public CompletableFuture<Proveedor> proveedor(ProveedorAsignado asignacion, DataLoader<UUID, Proveedor> cargador) {
        return cargador.load(asignacion.proveedorId());
    }

    @SchemaMapping(typeName = "ProveedorAsignado")
    public CompletableFuture<String> categoria(ProveedorAsignado asignacion, DataLoader<UUID, Proveedor> cargador) {
        return cargador.load(asignacion.proveedorId()).thenApply(p -> p == null ? null : p.categoria());
    }
}
