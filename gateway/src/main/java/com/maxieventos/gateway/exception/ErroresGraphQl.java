package com.maxieventos.gateway.exception;

import com.maxieventos.gateway.config.ReferenciaPeticionFilter;
import com.maxieventos.gateway.config.TokenInterceptor;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.convert.ConversionException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

/**
 * Traduce los errores de los servicios REST a errores GraphQL legibles:
 * <ul>
 *   <li>{@code classification}: 400 BAD_REQUEST, 401 UNAUTHORIZED, 403 FORBIDDEN, 404 NOT_FOUND, 409 CONFLICT,
 *       503 / caído / timeout SERVICE_UNAVAILABLE, otro INTERNAL_ERROR.</li>
 *   <li>{@code message}: el mensaje en español que devolvió el servicio.</li>
 *   <li>{@code extensions}: {@code servicio}, {@code status} y, si el servicio los envió, {@code errores: [{campo, mensaje}]}.</li>
 * </ul>
 * Nunca se devuelve un stack trace: lo inesperado se registra en el log y se responde con un mensaje neutro.
 */
@Component
public class ErroresGraphQl extends DataFetcherExceptionResolverAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ErroresGraphQl.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        AccesoDenegadoException denegado = buscar(ex, AccesoDenegadoException.class);
        if (denegado != null) {
            // Fase 11D: el rol no permite la operación; no se llamó a ningún servicio
            LOGGER.warn("Acceso denegado en el gateway ref={}: {} exige ADMINISTRADOR", referencia(env), denegado.getOperacion());
            Map<String, Object> extensiones = new LinkedHashMap<>();
            extensiones.put("servicio", "gateway");
            extensiones.put("status", 403);
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.FORBIDDEN).message(denegado.getMessage())
                    .extensions(extensiones).build();
        }
        ServicioRemotoException remota = buscar(ex, ServicioRemotoException.class);
        if (remota != null) {
            Map<String, Object> extensiones = new LinkedHashMap<>();
            extensiones.put("servicio", remota.getServicio());
            extensiones.put("status", remota.getStatus());
            if (!remota.getErrores().isEmpty()) {
                List<Map<String, String>> errores = remota.getErrores().stream()
                        .map(e -> {
                            Map<String, String> campo = new LinkedHashMap<>();
                            campo.put("campo", e.campo());
                            campo.put("mensaje", e.mensaje());
                            return campo;
                        })
                        .toList();
                extensiones.put("errores", errores);
            }
            if (remota.getStatus() >= 500) {
                // Fase 11C: la interfaz muestra un mensaje genérico con "Ref: <8 primeros caracteres de ref>"; el detalle
                // real es esta línea (buscarla con: docker compose logs gateway | Select-String "<código>")
                LOGGER.warn("Error técnico ref={} {} -> {} ({}): {}", referencia(env), env.getExecutionStepInfo().getPath(),
                        remota.getServicio(), remota.getStatus(), remota.getMessage(), remota.getCause());
            }
            return GraphqlErrorBuilder.newError(env).errorType(remota.clasificacion()).message(remota.getMessage())
                    .extensions(extensiones).build();
        }
        if (buscar(ex, BindException.class) != null || buscar(ex, ConversionException.class) != null) {
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST)
                    .message("Algún argumento no tiene el formato esperado (por ejemplo, un id que no es un UUID).")
                    .build();
        }
        LOGGER.error("Error inesperado ref={} al resolver {}", referencia(env), env.getExecutionStepInfo().getPath(), ex);
        return GraphqlErrorBuilder.newError(env).errorType(ErrorType.INTERNAL_ERROR)
                .message("Error interno del gateway. Intenta de nuevo; si persiste, revisa el log del gateway.").build();
    }

    /** El código de referencia de la petición (GraphQLContext; si no, el MDC del hilo; si no, "-"). */
    private static String referencia(DataFetchingEnvironment env) {
        Object enContexto = env.getGraphQlContext().get(TokenInterceptor.REFERENCIA);
        if (enContexto != null) {
            return enContexto.toString();
        }
        String enMdc = MDC.get(ReferenciaPeticionFilter.MDC_CLAVE);
        return enMdc != null ? enMdc : "-";
    }

    private static <T extends Throwable> T buscar(Throwable ex, Class<T> tipo) {
        for (Throwable actual = ex; actual != null; actual = actual.getCause()) {
            if (tipo.isInstance(actual)) {
                return tipo.cast(actual);
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return null;
    }
}
