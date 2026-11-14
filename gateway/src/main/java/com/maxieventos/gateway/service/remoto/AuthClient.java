package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.AuthPayload;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** auth-service. */
@Component
public class AuthClient extends ClienteRemoto {

    public AuthClient(@Qualifier("authRestClient") RestClient rest) {
        super(rest, "auth");
    }

    /** POST /auth/login (sin token). Credenciales incorrectas → UNAUTHORIZED con el mensaje de Auth. */
    public AuthPayload login(String correo, String password) {
        return ejecutar(rest.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("correo", correo, "password", password)), null, AuthPayload.class);
    }
}
