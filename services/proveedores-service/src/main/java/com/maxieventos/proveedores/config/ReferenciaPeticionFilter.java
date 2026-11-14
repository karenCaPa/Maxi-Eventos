package com.maxieventos.proveedores.config;

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
 * Código de referencia de la petición (Fase 11C; igual en los 10 servicios y en el gateway). El gateway reenvía el
 * {@code X-Request-Id} de la interfaz; aquí se pone en el MDC ({@code ref}) para que salga en cada línea del log
 * ({@code logging.pattern.correlation}) y se devuelve en la respuesta. Si falta o no tiene un formato seguro
 * ({@code [A-Za-z0-9-]}, de 8 a 64 caracteres) se genera un UUID. Va antes que la seguridad (también los 401 lo llevan).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReferenciaPeticionFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Request-Id";
    public static final String MDC_CLAVE = "ref";
    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

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
