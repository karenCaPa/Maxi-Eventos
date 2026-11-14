package com.maxieventos.clientes;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.clientes.repository.ClienteRepository;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.UUID;
import java.time.Instant;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.security.test.context.support.WithAnonymousUser;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:clientes-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.cache.type=simple",
        "auth.jwt.secret=test-secret-for-authentication-0123456789"
})
@WithMockUser
class ClienteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

        @Autowired
        private ClienteRepository repository;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @WithAnonymousUser
        void rechazaSolicitudesSinJwt() throws Exception {
            mockMvc.perform(get("/api/v1/clientes"))
                    .andExpect(status().isUnauthorized())
                    // Fase 7: el 401 de Spring Security usa el ErrorResponse común
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Debes iniciar sesión")))
                    .andExpect(jsonPath("$.path").value("/api/v1/clientes"));
        }

        /** Fase 11C: el código de referencia que reenvía el gateway vuelve en la respuesta, también en un 401 (el filtro va antes que la seguridad). */
        @Test
        @WithAnonymousUser
        void laReferenciaDeLaPeticionVuelveEnLaRespuestaAunqueSeaUn401() throws Exception {
            mockMvc.perform(get("/api/v1/clientes").header("X-Request-Id", "3f9a12bc-1111-4222-8333-444455556666"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("X-Request-Id", "3f9a12bc-1111-4222-8333-444455556666"));
        }

        @Test
        @WithAnonymousUser
        void unaReferenciaConCaracteresNoPermitidosSeReemplaza() throws Exception {
            MvcResult resultado = mockMvc.perform(get("/api/v1/clientes").header("X-Request-Id", "no valida; <script>"))
                    .andExpect(status().isUnauthorized()).andReturn();
            org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getHeader("X-Request-Id"))
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @WithAnonymousUser
        void rechazaJwtDeOtroEmisorConErrorResponse() throws Exception {
            SecretKey key = Keys.hmacShaKeyFor("test-secret-for-authentication-0123456789".getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder().subject("3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b").issuer("otro-emisor")
                    .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key).compact();
            mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("El token no es válido. Inicia sesión de nuevo."));
        }

        @Test
        @WithAnonymousUser
        void aceptaJwtValidoFirmadoConLaClaveCompartida() throws Exception {
            mockMvc.perform(get("/api/v1/clientes")
                            .header("Authorization", "Bearer " + tokenValido()))
                    .andExpect(status().isOk());
        }

        // ---- Fase 11D: autorización por rol en el propio servicio (con JWT reales que pasan por el filtro)

        @Test
        @WithAnonymousUser
        void elAsistentePuedeConsultarYCrearClientesPeroNoBorrarlos() throws Exception {
            String asistente = "Bearer " + token("ASISTENTE");
            mockMvc.perform(get("/api/v1/clientes").header("Authorization", asistente)).andExpect(status().isOk());
            MvcResult creado = mockMvc.perform(post("/api/v1/clientes").header("Authorization", asistente)
                            .contentType(MediaType.APPLICATION_JSON).content(requestValido()))
                    .andExpect(status().isCreated()).andReturn();
            String id = objectMapper.readTree(creado.getResponse().getContentAsString()).get("id").asText();

            mockMvc.perform(delete("/api/v1/clientes/" + id).header("Authorization", asistente))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación."))
                    .andExpect(jsonPath("$.path").value("/api/v1/clientes/" + id));
            mockMvc.perform(get("/api/v1/clientes/" + id).header("Authorization", asistente)).andExpect(status().isOk());

            mockMvc.perform(delete("/api/v1/clientes/" + id).header("Authorization", "Bearer " + token("ADMINISTRADOR")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithAnonymousUser
        void unTokenSinRolAnteriorALa11DNoPuedeBorrar() throws Exception {
            mockMvc.perform(delete("/api/v1/clientes/" + UUID.randomUUID()).header("Authorization", "Bearer " + tokenValido()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación."));
            // un rol que no existe tampoco da permisos
            mockMvc.perform(delete("/api/v1/clientes/" + UUID.randomUUID()).header("Authorization", "Bearer " + token("ADMIN")))
                    .andExpect(status().isForbidden());
        }

        /** Token como los anteriores a la Fase 11D: sin el claim rol. */
        private static String tokenValido() {
            return token(null);
        }

        private static String token(String rol) {
            String secret = "test-secret-for-authentication-0123456789";
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Instant now = Instant.now();
            // Fase 7: sub = id opaco (no el correo) e iss exigido por el filtro; Fase 11D: claim rol
            var builder = Jwts.builder().subject("3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b").issuer("maxi-eventos-auth")
                    .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)));
            if (rol != null) {
                builder.claim("rol", rol);
            }
            return builder.signWith(key).compact();
        }

        @org.junit.jupiter.api.BeforeEach
        void limpiarDatos() {
                repository.deleteAll();
        }

    @Test
    void creaConsultaListaActualizaYValidaClientes() throws Exception {
        String request = """
                {"nombreCompleto":"Maria Lopez","telefono":"3101234567","correo":"maria.lopez@example.com","direccion":"Calle 15"}
                """;

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.nombreCompleto").value("Maria Lopez"));

        mockMvc.perform(get("/api/v1/clientes").param("nombre", "Maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.totalElementos").value(1));

        String secondRequest = request.replace("3101234567", "3101234568")
                .replace("maria.lopez@example.com", "maria.segunda@example.com");
        UUID id = crearCliente(secondRequest);

        mockMvc.perform(get("/api/v1/clientes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("maria.segunda@example.com"));

        mockMvc.perform(put("/api/v1/clientes/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest.replace("Maria Lopez", "Maria Perez")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Maria Perez"));

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/clientes"));
    }

    @Test
    void devuelve404CuandoElClienteNoExiste() throws Exception {
        UUID unknownId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/clientes/" + unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/clientes/" + unknownId));
    }

    @Test
    void rechazaCamposQueNoCumplenElContrato() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreCompleto\":\"AB\",\"telefono\":\"123456\","
                                + "\"correo\":\"correo-invalido\",\"direccion\":\"" + "x".repeat(251) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/clientes"));
    }

    @Test
    void rechazaCorreoOTelefonoDuplicadosAlCrearYActualizar() throws Exception {
        String firstRequest = requestValido();
        String secondRequest = firstRequest.replace("3009876543", "3009876544")
                .replace("juan.perez@example.com", "juan.otro@example.com");
        UUID firstId = crearCliente(firstRequest);
        UUID secondId = crearCliente(secondRequest);

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest.replace("3009876543", "3009876555")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest.replace("juan.perez@example.com", "juan.tercero@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(put("/api/v1/clientes/" + secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest.replace("juan.otro@example.com", "juan.perez@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(put("/api/v1/clientes/" + secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest.replace("3009876544", "3009876543")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(put("/api/v1/clientes/" + firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isOk());
    }

    @Test
    void validaParametrosDePaginacion() throws Exception {
        mockMvc.perform(get("/api/v1/clientes").param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/clientes").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

        @Test
        void actualizaClienteInexistenteYDevuelve404() throws Exception {
                mockMvc.perform(put("/api/v1/clientes/" + UUID.randomUUID())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(requestValido()))
                                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR") // Fase 11D: borrar clientes solo ADMINISTRADOR
        void eliminaClienteYDevuelve204() throws Exception {
                UUID id = crearCliente(requestValido());

                mockMvc.perform(delete("/api/v1/clientes/" + id))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get("/api/v1/clientes/" + id))
                                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        void eliminaClienteInexistenteYDevuelve404() throws Exception {
                mockMvc.perform(delete("/api/v1/clientes/" + UUID.randomUUID()))
                                .andExpect(status().isNotFound());
        }

        private UUID crearCliente(String request) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/v1/clientes")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(request))
                                .andExpect(status().isCreated())
                                .andReturn();

                return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        }

        private String requestValido() {
                return "{\"nombreCompleto\":\"Juan Perez\",\"telefono\":\"3009876543\","
                                + "\"correo\":\"juan.perez@example.com\",\"direccion\":\"Carrera 7\"}";
        }
}
