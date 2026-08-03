package co.fleetsec.vapp.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limiter de login (V-07 remediado) — ventana deslizante por clave (IP del cliente).
 *
 * <p>Implementación in-memory sin dependencias externas (KISS): mantiene los timestamps de los
 * intentos recientes por clave y rechaza cuando se superan {@code max-attempts} dentro de
 * {@code window-seconds}. Cuenta <b>todos</b> los intentos (éxito o fallo) para frenar el
 * fuerza-bruta de credenciales.
 *
 * <p>Configurable vía {@code app.ratelimit.login.max-attempts} (def. 5) y
 * {@code app.ratelimit.login.window-seconds} (def. 60).
 *
 * <p><b>Alcance:</b> por-instancia. En despliegue multi-réplica el control real debe vivir en el
 * edge (WAF/API gateway) o en un store compartido (Redis); se documenta en la ficha V-07.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.ratelimit.login.max-attempts:5}") int maxAttempts,
            @Value("${app.ratelimit.login.window-seconds:60}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
    }

    /**
     * Registra un intento para {@code key} y devuelve {@code true} si está permitido, o
     * {@code false} si la clave superó el cupo dentro de la ventana.
     */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> attempts = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() > windowMillis) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }
}
