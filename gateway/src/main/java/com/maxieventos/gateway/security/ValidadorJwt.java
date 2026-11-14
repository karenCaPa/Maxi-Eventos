package com.maxieventos.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valida el JWT con el mismo patrón que los servicios ({@code Jwts.parser().verifyWith(...)}): firma HS256 con la clave
 * compartida {@code AUTH_JWT_SECRET}, vigencia ({@code exp}) y emisor ({@code iss}).
 */
@Component
public class ValidadorJwt {

    private static final String BEARER = "Bearer ";

    private final JwtParser parser;

    public ValidadorJwt(@Value("${auth.jwt.secret}") String secret, @Value("${auth.jwt.issuer}") String issuer) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(issuer)
                .build();
    }

    /**
     * @param autorizacion valor completo del header {@code Authorization}
     * @return los claims del token válido
     * @throws TokenRechazadoException si falta, venció o no es válido (firma, emisor, formato o sin {@code sub})
     */
    public Claims validar(String autorizacion) {
        if (autorizacion == null || autorizacion.isBlank()) {
            throw new TokenRechazadoException(TokenRechazadoException.FALTA, "sin token");
        }
        if (!autorizacion.startsWith(BEARER)) {
            throw new TokenRechazadoException(TokenRechazadoException.INVALIDO, "el header no es Bearer");
        }
        try {
            Claims claims = parser.parseSignedClaims(autorizacion.substring(BEARER.length()).trim()).getPayload();
            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                throw new TokenRechazadoException(TokenRechazadoException.INVALIDO, "token sin sub");
            }
            return claims;
        } catch (ExpiredJwtException ex) {
            throw new TokenRechazadoException(TokenRechazadoException.VENCIDO, "token vencido");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenRechazadoException(TokenRechazadoException.INVALIDO,
                    "token inválido (" + ex.getClass().getSimpleName() + ")");
        }
    }
}
