package com.maxieventos.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Código de referencia de cada petición (Fase 11C). La interfaz envía {@code X-Request-Id} (un UUID) y, ante un error
 * técnico, muestra "Ref: XXXXXXXX" (los 8 primeros caracteres) junto a un mensaje genérico; el error real queda en el log
 * con ese mismo código.
 * <ul>
 *   <li>Si el header falta o no tiene un formato seguro ({@code [A-Za-z0-9-]}, de 8 a 64 caracteres) se genera un UUID: así
 *       nunca entra en el log texto arbitrario del cliente (inyección de líneas).</li>
 *   <li>Se guarda en el MDC ({@code ref}): sale en cada línea del log ({@code logging.pattern.correlation}) y
 *       {@link RestClientConfig} lo reenvía a los servicios en el mismo header.</li>
 *   <li>Se devuelve en la respuesta ({@code X-Request-Id}).</li>
 * </ul>
 * Va antes que la seguridad, para que también los 401 lleven la referencia en el log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReferenciaPeticionFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Request-Id";
    public static final String MDC_CLAVE = "ref";
    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /** La referencia recibida si es segura; si no, una nueva. */
    public static String normalizar(String recibida) {
        return recibida != null && FORMATO.matcher(recibida).matches() ? recibida : UUID.randomUUID().toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String referencia = normalizar(request.getHeader(CABECERA));
        MDC.put(MDC_CLAVE, referencia);
        response.setHeader(CABECERA, referencia);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CLAVE);
        }
    }
}
