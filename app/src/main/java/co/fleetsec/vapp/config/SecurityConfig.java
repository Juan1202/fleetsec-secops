package co.fleetsec.vapp.config;

import co.fleetsec.vapp.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security — <b>V-11 remediado</b> (FSEC-17 Fase 2).
 *
 * <p>Matriz de protección (default-deny): solo el login y los docs son públicos;
 * <b>todo lo demás exige autenticación</b> por JWT (ver {@link JwtAuthFilter}).
 *
 * <p>La autorización por ownership (V-05, V-09) se verifica en los controllers con
 * el {@code AuthenticatedUser} del contexto (401 = sin token; 403 = sin permiso).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // API stateless con Bearer JWT → sin CSRF ni sesión de servidor.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Público: obtener token + documentación (el healthcheck usa /v3/api-docs).
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Todo lo demás (incluida /h2-console) requiere autenticación.
                        // Nota: en producción, la consola H2 se DESHABILITA (spring.h2.console.enabled=false).
                        .anyRequest().authenticated())
                // Sin token válido → 401 (no redirect ni 403). El 403 lo emiten los
                // controllers ante fallo de ownership (autenticado pero no autorizado).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // El filtro JWT corre antes del filtro de user/password estándar.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
