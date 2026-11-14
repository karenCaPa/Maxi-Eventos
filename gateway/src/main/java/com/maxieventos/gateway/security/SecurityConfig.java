package com.maxieventos.gateway.security;

import com.maxieventos.gateway.config.ReferenciaPeticionFilter;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad HTTP del gateway.
 * <ul>
 *   <li>{@code /graphql}: permitido a nivel HTTP; el {@code TokenInterceptor} exige un JWT válido salvo para la mutation
 *       login (la seguridad HTTP no ve qué operación GraphQL llega).</li>
 *   <li>{@code /graphiql}: solo existe con el perfil dev; es una página estática (sus consultas van a {@code /graphql}).</li>
 *   <li>Actuator: solo health es público; info y metrics exigen token.</li>
 *   <li>{@code /documentos/**} (descarga de PDF, Fase 10): exige token ({@code JwtAuthenticationFilter}).</li>
 *   <li>CORS explícito: solo los orígenes de {@code CORS_ALLOWED_ORIGINS}, nunca {@code *}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            RespuestasSeguridad respuestas) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/graphql", "/graphiql", "/graphiql/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores.authenticationEntryPoint(respuestas).accessDeniedHandler(respuestas))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * CORS solo para lo que la interfaz web (otro origen) necesita, con los mismos orígenes de {@code CORS_ALLOWED_ORIGINS}:
     * <ul>
     *   <li>{@code /graphql}: POST y OPTIONS; cabeceras Authorization, Content-Type y Accept.</li>
     *   <li>{@code /documentos/**} (Fase 10, descarga de PDF de solo lectura): GET y OPTIONS; cabeceras Authorization y
     *       Accept; expone {@code Content-Disposition} para que la interfaz lea el nombre del archivo.</li>
     * </ul>
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins:}") String origenes) {
        List<String> permitidos = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList();
        if (permitidos.stream().anyMatch(origen -> origen.contains("*"))) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS no admite '*': indica los orígenes exactos "
                    + "(por ejemplo http://localhost:5173).");
        }
        // Fase 11C: X-Request-Id (código de referencia de la interfaz) en las dos rutas; también se expone en la respuesta.
        CorsConfiguration graphql = base(permitidos);
        graphql.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        graphql.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                ReferenciaPeticionFilter.CABECERA));
        graphql.setExposedHeaders(List.of(ReferenciaPeticionFilter.CABECERA));

        CorsConfiguration documentos = base(permitidos);
        documentos.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.OPTIONS.name()));
        documentos.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.ACCEPT, ReferenciaPeticionFilter.CABECERA));
        documentos.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION, ReferenciaPeticionFilter.CABECERA));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/graphql", graphql);
        fuente.registerCorsConfiguration("/documentos/**", documentos);
        return fuente;
    }

    private static CorsConfiguration base(List<String> permitidos) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(permitidos);
        // El token viaja en el header Authorization, no en cookies: no hacen falta credenciales del navegador
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(Duration.ofHours(1));
        return configuracion;
    }
}
