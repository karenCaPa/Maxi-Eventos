package com.maxieventos.auth.service;

import com.maxieventos.auth.dto.LoginRequest;
import com.maxieventos.auth.dto.LoginResponse;
import com.maxieventos.auth.entity.Usuario;
import com.maxieventos.auth.repository.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Login (Fase 11D: usuarios de la tabla {@code usuarios}, cada uno con su rol). El token lleva {@code sub} = id opaco del
 * usuario, {@code iss}, {@code iat}, {@code exp} y {@code rol} (ADMINISTRADOR o ASISTENTE; no es un dato sensible). Nunca
 * lleva el correo ni el nombre (el payload de un JWT solo está codificado, no cifrado).
 */
@Service
public class AuthService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AuthService.class);
    static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos.";

    private final UsuarioRepository usuarios;
    private final String issuer;
    private final Duration tokenTtl;
    private final SecretKey signingKey;
    private final PasswordEncoder passwordEncoder;
    /** Hash de una contraseña que nadie conoce: con un correo inexistente también se compara (mismo tiempo de respuesta). */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios,
                       @Value("${auth.jwt.secret}") String jwtSecret,
                       @Value("${auth.jwt.issuer}") String issuer,
                       @Value("${auth.token-ttl}") Duration tokenTtl,
                       PasswordEncoder passwordEncoder) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.usuarios = usuarios;
        this.issuer = issuer;
        this.tokenTtl = tokenTtl;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.passwordEncoder = passwordEncoder;
        this.hashFicticio = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        Optional<Usuario> encontrado = usuarios.findByCorreo(Usuario.normalizarCorreo(request.correo()));
        boolean claveCorrecta = passwordEncoder.matches(request.password(),
                encontrado.map(Usuario::getPasswordHash).orElse(hashFicticio));
        if (encontrado.isEmpty() || !claveCorrecta || !encontrado.get().isActivo()) {
            LOGGER.warn("Login fallido. correo={}", enmascarar(request.correo()));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS);
        }
        Usuario usuario = encontrado.get();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenTtl);
        String token = Jwts.builder()
                .subject(usuario.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("rol", usuario.getRol().name())
                .signWith(signingKey)
                .compact();
        // El token no se guarda en ningún sitio: no hay logout ni revocación (decisión del proyecto).
        LOGGER.info("Login correcto. correo={}, rol={}, expira={}", enmascarar(usuario.getCorreo()), usuario.getRol(), expiresAt);
        return new LoginResponse(token, "Bearer", tokenTtl.toSeconds(), usuario.getNombre(), usuario.getRol().name());
    }

    /** Correo enmascarado para los logs: o***@maxieventos.com. Nunca se registra la contraseña ni el token. */
    static String enmascarar(String correo) {
        if (correo == null || correo.isBlank()) {
            return "(vacío)";
        }
        int arroba = correo.indexOf('@');
        return arroba <= 0 ? "***" : correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
