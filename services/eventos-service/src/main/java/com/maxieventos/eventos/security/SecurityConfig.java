package com.maxieventos.eventos.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Públicos: health de actuator y la documentación (Swagger, /v3/api-docs). Todo lo demás exige un JWT válido,
 * también info y metrics de actuator. Sin CORS: los navegadores solo hablan con el gateway.
 */
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            RespuestasSeguridad respuestas) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Fase 11D: el bloqueo / liberación manual de fechas solo ADMINISTRADOR (el asistente y un token sin
                        // rol → 403). Consultar fechas y todo el flujo del evento, cualquier usuario autenticado.
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/fechas/*/estado").hasRole("ADMINISTRADOR")
                        // Fase 11E: ejecutar los recordatorios a mano, solo ADMINISTRADOR
                        .requestMatchers("/api/v1/recordatorios/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(respuestas).accessDeniedHandler(respuestas))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
