package com.maxieventos.gateway.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticación HTTP por JWT para las rutas del gateway que no son GraphQL (actuator distinto de health). Mismo
 * comportamiento que el filtro de los servicios. {@code /graphql} no pasa por aquí: allí decide el {@code TokenInterceptor}
 * (la seguridad HTTP no puede distinguir la mutation login del resto, porque todo entra por el mismo endpoint).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ValidadorJwt validador;
    private final RespuestasSeguridad respuestas;

    public JwtAuthenticationFilter(ValidadorJwt validador, RespuestasSeguridad respuestas) {
        this.validador = validador;
        this.respuestas = respuestas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/graphql");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String autorizacion = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (autorizacion != null && !autorizacion.isBlank()) {
            try {
                Claims claims = validador.validar(autorizacion);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of()));
            } catch (TokenRechazadoException ex) {
                SecurityContextHolder.clearContext();
                respuestas.escribir(request, response, HttpStatus.UNAUTHORIZED, ex.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
