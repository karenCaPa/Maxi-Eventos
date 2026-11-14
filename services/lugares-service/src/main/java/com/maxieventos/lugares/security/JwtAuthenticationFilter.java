package com.maxieventos.lugares.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT de cada petición: firma HS256 con la clave compartida {@code AUTH_JWT_SECRET}, vigencia y emisor
 * ({@code iss}). El servicio lo valida aunque la llamada venga del gateway o de otro servicio (defensa en profundidad).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtParser parser;
    private final RespuestasSeguridad respuestas;

    public JwtAuthenticationFilter(@Value("${auth.jwt.secret}") String secret, @Value("${auth.jwt.issuer}") String issuer,
                                   RespuestasSeguridad respuestas) {
        if (secret.length() < 32) throw new IllegalArgumentException("AUTH_JWT_SECRET debe tener al menos 32 caracteres");
        this.parser = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(issuer).build();
        this.respuestas = respuestas;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String rechazo = null;
            try {
                Claims claims = parser.parseSignedClaims(authorization.substring(7)).getPayload();
                if (claims.getSubject() != null && !claims.getSubject().isBlank()) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, autoridades(claims)));
                } else {
                    rechazo = RespuestasSeguridad.TOKEN_INVALIDO;
                }
            } catch (ExpiredJwtException exception) {
                rechazo = RespuestasSeguridad.TOKEN_VENCIDO;
            } catch (JwtException | IllegalArgumentException exception) {
                rechazo = RespuestasSeguridad.TOKEN_INVALIDO;
            }
            if (rechazo != null) {
                SecurityContextHolder.clearContext();
                respuestas.escribir(request, response, HttpStatus.UNAUTHORIZED, rechazo);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Fase 11D: el claim {@code rol} da la autoridad {@code ROLE_<rol>} (ADMINISTRADOR o ASISTENTE). Un token sin rol
     * (emitido antes de la 11D) o con otro valor queda autenticado pero SIN ninguna autoridad: no pasa ningún
     * {@code hasRole(...)} y recibe 403 en lo que exige un rol.
     */
    static java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> autoridades(Claims claims) {
        Object rol = claims.get("rol");
        return "ADMINISTRADOR".equals(rol) || "ASISTENTE".equals(rol)
                ? java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + rol))
                : java.util.List.of();
    }
}
