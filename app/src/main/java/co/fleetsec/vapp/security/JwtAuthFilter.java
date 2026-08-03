package co.fleetsec.vapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de autenticación por JWT (V-11). Extrae el Bearer token, lo valida con
 * {@link JwtService} (que rechaza alg:none, firma inválida y expiración — V-02), y
 * puebla el {@link SecurityContextHolder} con un {@link AuthenticatedUser}.
 *
 * <p>Si no hay token o es inválido, NO se establece autenticación → los endpoints
 * protegidos responden 401 (lo maneja Spring Security, no este filtro).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            try {
                JwtService.TokenClaims claims = jwt.validateToken(header.substring(BEARER.length()));
                AuthenticatedUser principal =
                        new AuthenticatedUser(claims.sub(), claims.role(), claims.driverId());
                var authority = new SimpleGrantedAuthority("ROLE_" + claims.role());
                var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (IllegalArgumentException e) {
                // Token inválido (incluye alg:none / firma / expiración): sin autenticación.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
