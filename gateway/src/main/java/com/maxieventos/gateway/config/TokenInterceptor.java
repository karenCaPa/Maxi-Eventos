package com.maxieventos.gateway.config;

import com.maxieventos.gateway.security.TokenRechazadoException;
import com.maxieventos.gateway.security.ValidadorJwt;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.parser.Parser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Seguridad de {@code /graphql} (Fase 7) y reenvío del token.
 * <ol>
 *   <li>Si la operación que se va a ejecutar es una {@code mutation} que solo contiene el campo {@code login}
 *       (y, si acaso, {@code __typename}), pasa sin token.</li>
 *   <li>Cualquier otra operación (incluida la introspección del schema) exige un JWT válido: firma, vigencia y emisor.
 *       Si falta, venció o no es válido, se responde un error {@code UNAUTHORIZED} SIN ejecutar la operación, así que no
 *       se llama a ningún servicio (en el log no aparece ninguna línea {@code REST ->}).</li>
 *   <li>Con un token válido, el header {@code Authorization} se copia al {@code GraphQLContext}, de donde lo toman los
 *       clientes REST para reenviarlo: los servicios lo vuelven a validar (defensa en profundidad). Se usa el contexto de
 *       GraphQL (y no {@code RequestContextHolder}) porque los campos pueden resolverse en otro hilo.</li>
 * </ol>
 */
@Component
public class TokenInterceptor implements WebGraphQlInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenInterceptor.class);

    /** Clave del header en el {@code GraphQLContext}. */
    public static final String AUTORIZACION = "authorization";
    /** Clave del código de referencia de la petición en el {@code GraphQLContext} (Fase 11C). */
    public static final String REFERENCIA = "ref";
    /** Clave del rol del token (claim {@code rol}) en el {@code GraphQLContext} (Fase 11D; ver {@code Permisos}). */
    public static final String ROL = "rol";

    private final ValidadorJwt validador;

    public TokenInterceptor(ValidadorJwt validador) {
        this.validador = validador;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        // Fase 11C: la referencia (del MDC, la puso ReferenciaPeticionFilter en este mismo hilo) también al GraphQLContext,
        // para registrarla con el error aunque un campo se resuelva en otro hilo (ErroresGraphQl).
        String referencia = MDC.get(ReferenciaPeticionFilter.MDC_CLAVE);
        if (referencia != null) {
            request.configureExecutionInput((entrada, builder) ->
                    builder.graphQLContext(contexto -> contexto.put(REFERENCIA, referencia)).build());
        }
        if (soloLogin(request.getDocument(), request.getOperationName())) {
            return chain.next(request);
        }
        String autorizacion = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String rol;
        try {
            Object claimRol = validador.validar(autorizacion).get(ROL);
            rol = claimRol instanceof String texto ? texto : null; // sin rol (token anterior a la 11D): no es administrador
        } catch (TokenRechazadoException ex) {
            LOGGER.warn("GraphQL rechazado en el gateway: {} (operación {}); no se llamó a ningún servicio",
                    ex.getMotivo(), request.getOperationName() == null ? "sin nombre" : request.getOperationName());
            return Mono.just(rechazo(request, ex.getMessage()));
        }
        request.configureExecutionInput((entrada, builder) -> builder.graphQLContext(contexto -> {
            contexto.put(AUTORIZACION, autorizacion);
            if (rol != null) {
                contexto.put(ROL, rol);
            }
        }).build());
        return chain.next(request);
    }

    /**
     * {@code true} si la operación a ejecutar es una mutation cuyos campos son solo {@code login} (y {@code __typename}).
     * Ante cualquier duda (documento inválido, fragmentos, varias operaciones sin nombre) devuelve {@code false}: se exige token.
     */
    static boolean soloLogin(String documento, String nombreOperacion) {
        Document arbol;
        try {
            arbol = Parser.parse(documento);
        } catch (RuntimeException ex) {
            return false;
        }
        List<OperationDefinition> operaciones = arbol.getDefinitionsOfType(OperationDefinition.class);
        OperationDefinition operacion;
        if (nombreOperacion == null || nombreOperacion.isBlank()) {
            operacion = operaciones.size() == 1 ? operaciones.get(0) : null;
        } else {
            operacion = operaciones.stream().filter(o -> nombreOperacion.equals(o.getName())).findFirst().orElse(null);
        }
        if (operacion == null || operacion.getOperation() != OperationDefinition.Operation.MUTATION) {
            return false;
        }
        boolean hayLogin = false;
        for (Selection<?> seleccion : operacion.getSelectionSet().getSelections()) {
            if (!(seleccion instanceof Field campo)) {
                return false;
            }
            if (campo.getName().equals("login")) {
                hayLogin = true;
            } else if (!campo.getName().equals("__typename")) {
                return false;
            }
        }
        return hayLogin;
    }

    /** Respuesta GraphQL con un solo error UNAUTHORIZED y sin {@code data} (la operación no se ejecutó). */
    private static WebGraphQlResponse rechazo(WebGraphQlRequest request, String mensaje) {
        Map<String, Object> extensiones = new LinkedHashMap<>();
        extensiones.put("servicio", "gateway");
        extensiones.put("status", 401);
        GraphQLError error = GraphqlErrorBuilder.newError().errorType(ErrorType.UNAUTHORIZED).message(mensaje)
                .extensions(extensiones).build();
        ExecutionResult resultado = ExecutionResult.newExecutionResult().addError(error).build();
        return new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), resultado));
    }
}
