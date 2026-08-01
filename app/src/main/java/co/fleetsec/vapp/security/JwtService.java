package co.fleetsec.vapp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio JWT construido a mano — <b>intencionalmente vulnerable</b>.
 *
 * <p><b>V02 · Broken Auth / JWT alg:none (CWE-345).</b> {@link #validateToken(String)}
 * confía en el campo {@code alg} del header enviado por el cliente. Si un atacante forja
 * un token con {@code "alg":"none"} y sin firma, el servicio lo acepta y devuelve la
 * identidad reclamada — permitiendo suplantar a cualquier usuario (p. ej. role=ADMIN).
 *
 * <p>Se implementa sin librería JWT a propósito: las libs modernas (jjwt, nimbus) rechazan
 * {@code alg:none} por defecto, lo que impediría reproducir el vector.
 *
 * <p>La firma HMAC usa {@code app.jwt.secret} (V10 · hardcoded en application.yml).
 */
@Service
public class JwtService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();
    private static final long TTL_SECONDS = 3600;

    private final String secret;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret;
    }

    /** Identidad extraída de un token. */
    public record TokenClaims(String sub, String role) {
    }

    /** Emite un token HS256 firmado con el secreto de la app. */
    public String generateToken(String username, String role) {
        try {
            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long now = System.currentTimeMillis() / 1000;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", username);
            payload.put("role", role);
            payload.put("iat", now);
            payload.put("exp", now + TTL_SECONDS);

            String h = B64URL.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String p = B64URL.encodeToString(MAPPER.writeValueAsBytes(payload));
            String signingInput = h + "." + p;
            String sig = B64URL.encodeToString(hmacSha256(signingInput));
            return signingInput + "." + sig;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el token", e);
        }
    }

    /**
     * Valida un token y devuelve su identidad.
     *
     * <p>VULN V02: si {@code alg} es {@code none}, acepta el token sin verificar firma.
     */
    public TokenClaims validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Token malformado");
            }

            JsonNode header = MAPPER.readTree(B64URL_DEC.decode(parts[0]));
            JsonNode payload = MAPPER.readTree(B64URL_DEC.decode(parts[1]));
            String alg = header.path("alg").asText("");

            // ── V02 · JWT alg:none ──────────────────────────────────────────────
            // El servidor confía en el algoritmo declarado por el cliente. Con "none"
            // no se exige firma y el token forjado se acepta tal cual.
            if ("none".equalsIgnoreCase(alg)) {
                return claimsFrom(payload);
            }

            // Ruta "segura" (HS256): verifica HMAC. La falla está en la rama de arriba.
            if (parts.length != 3) {
                throw new IllegalArgumentException("Token HS256 sin firma");
            }
            String expected = B64URL.encodeToString(hmacSha256(parts[0] + "." + parts[1]));
            if (!constantTimeEquals(expected, parts[2])) {
                throw new IllegalArgumentException("Firma inválida");
            }
            return claimsFrom(payload);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Token inválido", e);
        }
    }

    private TokenClaims claimsFrom(JsonNode payload) {
        return new TokenClaims(payload.path("sub").asText(null), payload.path("role").asText(null));
    }

    private byte[] hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
