package com.maxieventos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.security.TokensDePrueba;
import com.maxieventos.gateway.service.remoto.ClientesClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** De punta a punta por HTTP: con un token válido, el header Authorization llega al cliente REST; sin token, no se llama. */
@SpringBootTest(properties = {TokensDePrueba.PROP_SECRETO, TokensDePrueba.PROP_CORS})
@AutoConfigureMockMvc
class TokenInterceptorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClientesClient clientes;

    private final UUID id = UUID.randomUUID();

    private MvcResult consultar(String autorizacion) throws Exception {
        var peticion = post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"{ cliente(id: \\\"" + id + "\\\") { nombreCompleto } }\"}");
        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        MvcResult inicio = mvc.perform(peticion).andReturn();
        if (inicio.getRequest().isAsyncStarted()) {
            return mvc.perform(asyncDispatch(inicio)).andExpect(status().isOk()).andReturn();
        }
        return inicio;
    }

    @Test
    void elTokenValidoLlegaAlClienteRest() throws Exception {
        String token = TokensDePrueba.valido();
        when(clientes.obtener(eq(token), eq(id)))
                .thenReturn(new Cliente(id, "Lucía Fernández", "3001112233", "lucia@ejemplo.com", null));

        MvcResult resultado = consultar(token);

        assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Lucía Fernández");
        verify(clientes).obtener(token, id);
    }

    /** Fase 7: antes, sin header se llamaba al servicio con null y él respondía 401; ahora lo rechaza el gateway. */
    @Test
    void sinHeaderNoSeLlamaAlClienteRest() throws Exception {
        MvcResult resultado = consultar(null);

        assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("UNAUTHORIZED");
        verifyNoInteractions(clientes);
    }

    @Test
    void graphiqlApagadoSinElPerfilDev() throws Exception {
        mvc.perform(get("/graphiql")).andExpect(status().isNotFound());
    }

    @Test
    void healthPublico() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    // ------------------------------------------------------------ qué operación pasa sin token

    @Test
    void soloLaMutationLoginPasaSinToken() {
        assertThat(TokenInterceptor.soloLogin("mutation { login(correo: \"a\", password: \"b\") { token } }", null)).isTrue();
        assertThat(TokenInterceptor.soloLogin("mutation Entrar { __typename login(correo: \"a\", password: \"b\") { token } }",
                "Entrar")).isTrue();
    }

    @Test
    void cualquierOtraOperacionExigeToken() {
        // login junto con otra mutation
        assertThat(TokenInterceptor.soloLogin(
                "mutation { login(correo: \"a\", password: \"b\") { token } cancelarEvento(id: \"x\") { estado } }", null)).isFalse();
        // queries, introspección y solo __typename
        assertThat(TokenInterceptor.soloLogin("{ evento(id: \"x\") { id } }", null)).isFalse();
        assertThat(TokenInterceptor.soloLogin("{ __schema { queryType { name } } }", null)).isFalse();
        assertThat(TokenInterceptor.soloLogin("mutation { __typename }", null)).isFalse();
        // login escondido en un fragmento
        assertThat(TokenInterceptor.soloLogin(
                "mutation { ...F } fragment F on Mutation { login(correo: \"a\", password: \"b\") { token } }", null)).isFalse();
        // dos operaciones: se evalúa la que se ejecuta (operationName)
        String dos = "mutation A { login(correo: \"a\", password: \"b\") { token } } mutation B { cancelarEvento(id: \"x\") { estado } }";
        assertThat(TokenInterceptor.soloLogin(dos, "A")).isTrue();
        assertThat(TokenInterceptor.soloLogin(dos, "B")).isFalse();
        assertThat(TokenInterceptor.soloLogin(dos, null)).isFalse();
        // documento con errores de sintaxis
        assertThat(TokenInterceptor.soloLogin("mutation { login(", null)).isFalse();
    }
}
