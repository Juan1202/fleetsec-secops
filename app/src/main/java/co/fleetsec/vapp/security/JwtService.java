package co.fleetsec.vapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio JWT basado en jjwt — <b>V-02 remediado</b> (FSEC-17, decisión en ADR-010).
 *
 * <p>La verificación usa {@code Jwts.parser().verifyWith(key)...parseSignedClaims()}, que
 * <b>rechaza</b> tokens sin firma ({@code alg:none}), con firma inválida, con un algoritmo
 * distinto al de la clave, o expirados. No se implementa criptografía a mano
 * ("don't roll your own crypto").
 *
 * <p>El secreto se lee de {@code app.jwt.secret} (variable de entorno, V-10 remediado).
 * HS256 exige una clave &ge; 32 bytes; {@link Keys#hmacShaKeyFor} lo valida.
 */
@Service
public class JwtService {

    private static final long TTL_SECONDS = 3600;

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Identidad extraída de un token. */
    public record TokenClaims(String sub, String role) {
    }

    /** Emite un token HS256 firmado con la clave de la app. */
    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TTL_SECONDS)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida firma y vigencia del token y devuelve su identidad.
     *
     * @throws IllegalArgumentException si el token no tiene firma válida (incluye
     *         {@code alg:none}), está expirado, o es malformado. AuthController mapea
     *         esta excepción a {@code 401}.
     */
    public TokenClaims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new TokenClaims(claims.getSubject(), claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Token inválido", e);
        }
    }
}
