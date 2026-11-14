package com.maxieventos.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Tokens para las pruebas, firmados con una clave que solo existe en las pruebas. */
public final class TokensDePrueba {

    public static final String SECRETO = "clave-jwt-solo-para-pruebas-del-gateway-0123456789";
    public static final String EMISOR = "maxi-eventos-auth";
    /** Propiedades para {@code @SpringBootTest}: la clave de prueba y un origen CORS permitido. */
    public static final String PROP_SECRETO = "auth.jwt.secret=" + SECRETO;
    public static final String PROP_CORS = "cors.allowed-origins=http://localhost:5173, http://127.0.0.1:5173";

    private TokensDePrueba() {
    }

    /** Token del ADMINISTRADOR (Fase 11D: claim rol). */
    public static String valido() {
        return "Bearer " + firmar(SECRETO, EMISOR, Instant.now().plus(Duration.ofHours(1)), "ADMINISTRADOR");
    }

    public static String asistente() {
        return "Bearer " + firmar(SECRETO, EMISOR, Instant.now().plus(Duration.ofHours(1)), "ASISTENTE");
    }

    /** Como los emitidos antes de la Fase 11D: firmado y vigente, sin el claim rol. */
    public static String sinRol() {
        return "Bearer " + firmar(SECRETO, EMISOR, Instant.now().plus(Duration.ofHours(1)), null);
    }

    public static String vencido() {
        return "Bearer " + firmar(SECRETO, EMISOR, Instant.now().minus(Duration.ofMinutes(1)));
    }

    public static String malFirmado() {
        return "Bearer " + firmar("otra-clave-que-no-es-la-del-proyecto-0123456789", EMISOR,
                Instant.now().plus(Duration.ofHours(1)));
    }

    public static String otroEmisor() {
        return "Bearer " + firmar(SECRETO, "otro-emisor", Instant.now().plus(Duration.ofHours(1)));
    }

    /** Token válido al que se le cambia la carga (payload) sin volver a firmarlo. */
    public static String manipulado() {
        String[] partes = valido().substring("Bearer ".length()).split("\\.");
        String carga = new String(java.util.Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("\"sub\":\"", "\"sub\":\"x");
        String cargaNueva = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(carga.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + partes[0] + "." + cargaNueva + "." + partes[2];
    }

    private static String firmar(String secreto, String emisor, Instant expira) {
        return firmar(secreto, emisor, expira, "ADMINISTRADOR");
    }

    private static String firmar(String secreto, String emisor, Instant expira, String rol) {
        Instant emitido = expira.minus(Duration.ofHours(2));
        var builder = Jwts.builder()
                .subject("3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b")
                .issuer(emisor)
                .issuedAt(Date.from(emitido))
                .expiration(Date.from(expira));
        if (rol != null) {
            builder.claim("rol", rol);
        }
        return builder.signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
