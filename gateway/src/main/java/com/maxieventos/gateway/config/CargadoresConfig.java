package com.maxieventos.gateway.config;

import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.service.remoto.ProveedoresClient;
import graphql.GraphQLContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import reactor.core.publisher.Mono;

/**
 * DataLoader de proveedores por id, compartido por {@code ProveedorAsignado.proveedor} y {@code ProveedorAsignado.categoria}:
 * en una misma petición GraphQL, cada proveedor distinto se consulta una sola vez.
 */
@Configuration
public class CargadoresConfig {

    public CargadoresConfig(BatchLoaderRegistry registro, ProveedoresClient proveedores) {
        registro.forTypePair(UUID.class, Proveedor.class).registerMappedBatchLoader((ids, entorno) -> {
            String autorizacion = entorno.getContext() instanceof GraphQLContext contexto
                    ? contexto.get(TokenInterceptor.AUTORIZACION) : null;
            return Mono.fromCallable(() -> {
                Map<UUID, Proveedor> porId = new LinkedHashMap<>();
                for (UUID id : ids) {
                    Proveedor proveedor = proveedores.obtenerOpcional(autorizacion, id);
                    if (proveedor != null) {
                        porId.put(id, proveedor);
                    }
                }
                return porId;
            });
        });
    }
}
