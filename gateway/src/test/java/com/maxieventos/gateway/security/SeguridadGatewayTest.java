package com.maxieventos.gateway.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.gateway.dto.AuthPayload;
import com.maxieventos.gateway.service.remoto.AuthClient;
import com.maxieventos.gateway.service.remoto.ClientesClient;
import com.maxieventos.gateway.service.remoto.EventosClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Seguridad del gateway por HTTP: JWT en /graphql, login sin token, CORS y actuator. */
@SpringBootTest(properties = {TokensDePrueba.PROP_SECRETO, TokensDePrueba.PROP_CORS})
@AutoConfigureMockMvc
class SeguridadGatewayTest {

    private static final String PERMITIDO = "http://localhost:5173";
    private static final String NO_PERMITIDO = "http://sitio-ajeno.example";
    private static final String CONSULTA = "{\"query\":\"{ eventos { totalElementos } }\"}";
    private static final String LOGIN =
            "{\"query\":\"mutation { login(correo: \\\"organizadora@ejemplo.com\\\", password: \\\"x\\\") { tipoToken } }\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AuthClient auth;
    @MockitoBean private ClientesClient clientes;
    @MockitoBean private EventosClient eventos;

    private ResultActions graphql(String cuerpo, String autorizacion) throws Exception {
        var peticion = post("/graphql").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        ResultActions primera = mvc.perform(peticion);
        var inicio = primera.andReturn();
        return inicio.getRequest().isAsyncStarted() ? mvc.perform(asyncDispatch(inicio)) : primera;
    }

    private void rechazado(String autorizacion, String mensaje) throws Exception {
        graphql(CONSULTA, autorizacion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].message").value(mensaje))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errors[0].extensions.status").value(401))
                .andExpect(jsonPath("$.errors[0].extensions.servicio").value("gateway"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(eventos, clientes);
    }

    // ------------------------------------------------------------ JWT en /graphql

    @Test
    void sinTokenUnauthorizedSinLlamarANadie() throws Exception {
        rechazado(null, TokenRechazadoException.FALTA);
    }

    @Test
    void tokenVencidoUnauthorized() throws Exception {
        rechazado(TokensDePrueba.vencido(), TokenRechazadoException.VENCIDO);
    }

    @Test
    void tokenMalFirmadoUnauthorized() throws Exception {
        rechazado(TokensDePrueba.malFirmado(), TokenRechazadoException.INVALIDO);
    }

    @Test
    void tokenManipuladoUnauthorized() throws Exception {
        rechazado(TokensDePrueba.manipulado(), TokenRechazadoException.INVALIDO);
    }

    @Test
    void tokenDeOtroEmisorUnauthorized() throws Exception {
        rechazado(TokensDePrueba.otroEmisor(), TokenRechazadoException.INVALIDO);
    }

    @Test
    void headerQueNoEsBearerUnauthorized() throws Exception {
        rechazado("Basic b3JnYW5pemFkb3JhOng=", TokenRechazadoException.INVALIDO);
    }

    @Test
    void loginSinTokenPermitido() throws Exception {
        when(auth.login("organizadora@ejemplo.com", "x")).thenReturn(new AuthPayload("eyJ...", "Bearer", 28800, "Organizadora", "ADMINISTRADOR"));

        graphql(LOGIN, null)
                .andExpect(jsonPath("$.data.login.tipoToken").value("Bearer"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verify(auth).login("organizadora@ejemplo.com", "x");
    }

    @Test
    void loginJuntoAOtraMutationExigeToken() throws Exception {
        String cuerpo = "{\"query\":\"mutation { login(correo: \\\"a@b.co\\\", password: \\\"x\\\") { tipoToken } "
                + "cancelarEvento(id: \\\"3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b\\\") { estado } }\"}";
        graphql(cuerpo, null)
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(auth, eventos);
    }

    @Test
    void introspeccionSinTokenUnauthorizedYApagadaFueraDeDev() throws Exception {
        String introspeccion = "{\"query\":\"{ __schema { queryType { name } } }\"}";
        graphql(introspeccion, null)
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("UNAUTHORIZED"));
        // Con token pero sin el perfil dev: graphql-java no permite la introspección
        graphql(introspeccion, TokensDePrueba.valido())
                .andExpect(jsonPath("$.errors[0].message").exists())
                .andExpect(jsonPath("$.data.__schema").doesNotExist());
    }

    // ------------------------------------------------------------ CORS

    @Test
    void preflightDeUnOrigenPermitido() throws Exception {
        mvc.perform(options("/graphql").header("Origin", PERMITIDO)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDO))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflightDeUnOrigenNoPermitido() throws Exception {
        mvc.perform(options("/graphql").header("Origin", NO_PERMITIDO)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization, content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void peticionRealDesdeUnOrigenPermitidoLlevaLaCabeceraCors() throws Exception {
        when(auth.login("organizadora@ejemplo.com", "x")).thenReturn(new AuthPayload("eyJ...", "Bearer", 28800, "Organizadora", "ADMINISTRADOR"));
        mvc.perform(post("/graphql").header("Origin", PERMITIDO).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDO));
    }

    @Test
    void corsNoAdmiteAsterisco() {
        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource("http://localhost:5173, *"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'*'");
    }

    // ------------------------------------------------------------ actuator y rutas

    @Test
    void actuatorSoloHealthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(RespuestasSeguridad.SIN_SESION))
                .andExpect(jsonPath("$.path").value("/actuator/info"));
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info").header("Authorization", TokensDePrueba.vencido()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(TokenRechazadoException.VENCIDO));
        mvc.perform(get("/actuator/info").header("Authorization", TokensDePrueba.valido())).andExpect(status().isOk());
    }
}
