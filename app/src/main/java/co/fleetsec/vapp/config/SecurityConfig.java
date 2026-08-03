package co.fleetsec.vapp.config;

import co.fleetsec.vapp.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Configuración de Spring Security — <b>V-11 remediado</b> (FSEC-17 Fase 2).
 *
 * <p>Matriz de protección (default-deny): solo el login y los docs son públicos;
 * <b>todo lo demás exige autenticación</b> por JWT (ver {@link JwtAuthFilter}).
 *
 * <p>La autorización por ownership (V-05, V-09) se verifica en los controllers con
 * el {@code AuthenticatedUser} del contexto (401 = sin token; 403 = sin permiso).
 *
 * <p>Añade además <b>security headers</b> (defensa en profundidad): HSTS, CSP,
 * X-Frame-Options (DENY), X-Content-Type-Options (nosniff), Referrer-Policy y
 * Permissions-Policy.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // API stateless con Bearer JWT → sin CSRF ni sesión de servidor.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ── Security headers (defensa en profundidad) ───────────────────
                // HSTS se emite solo sobre HTTPS (comportamiento correcto). frameOptions DENY:
                // la consola H2 es dev-only y se deshabilita en prod. CSP restrictiva apta para
                // una API JSON; el import OpenAPI del DAST usa /v3/api-docs, no se ve afectado.
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
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
