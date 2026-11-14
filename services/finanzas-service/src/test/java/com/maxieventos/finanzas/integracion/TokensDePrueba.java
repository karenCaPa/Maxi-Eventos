package com.maxieventos.finanzas.integracion;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Tokens para las pruebas de integración, firmados con una clave que solo existe en las pruebas. */
final class TokensDePrueba {

    static final String SECRETO = "clave-jwt-solo-para-pruebas-de-finanzas-0123456789";
    static final String EMISOR = "maxi-eventos-auth";

    private TokensDePrueba() {
    }

    static String valido() {
        return "Bearer " + firmar(SECRETO, EMISOR, Instant.now().plus(Duration.ofHours(1)));
    }

    static String vencido() {
        return "Bearer " + firmar(SECRETO, EMISOR, Instant.now().minus(Duration.ofMinutes(1)));
    }

    static String malFirmado() {
        return "Bearer " + firmar("otra-clave-que-no-es-la-del-proyecto-0123456789", EMISOR,
                Instant.now().plus(Duration.ofHours(1)));
    }

    private static String firmar(String secreto, String emisor, Instant expira) {
        return Jwts.builder()
                .subject("3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b")
                .issuer(emisor)
                .issuedAt(Date.from(expira.minus(Duration.ofHours(2))))
                .expiration(Date.from(expira))
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
