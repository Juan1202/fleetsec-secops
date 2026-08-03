package co.fleetsec.vapp.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test dual de V-03 (SSRF) a nivel del guard. Usa IPs literales para no depender de DNS
 * de red: {@code InetAddress.getAllByName} sobre un literal no hace resolución externa.
 */
class SsrfGuardTest {

    // ── Rechazo ──────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("V-03 rechaza: el IMDS de AWS (169.254.169.254) está bloqueado")
    void rejects_imdsLinkLocal() {
        assertThatThrownBy(() -> SsrfGuard.assertAllowed("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
    }

    @Test
    @DisplayName("V-03 rechaza: loopback y rangos privados (RFC 1918)")
    void rejects_loopbackAndPrivate() {
        assertThatThrownBy(() -> SsrfGuard.assertAllowed("http://127.0.0.1:8080/"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.assertAllowed("http://10.0.0.5/"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.assertAllowed("http://192.168.1.1/admin"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.assertAllowed("http://172.16.0.10/"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
    }

    @Test
    @DisplayName("V-03 rechaza: esquemas no-HTTP (file://, gopher://)")
    void rejects_nonHttpSchemes() {
        assertThatThrownBy(() -> SsrfGuard.assertAllowed("file:///etc/passwd"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
        assertThatThrownBy(() -> SsrfGuard.assertAllowed("gopher://169.254.169.254/"))
                .isInstanceOf(SsrfGuard.BlockedTargetException.class);
    }

    // ── Flujo legítimo ───────────────────────────────────────────────────────────
    @Test
    @DisplayName("V-03 legítimo: una URL HTTPS a una IP pública pasa la validación")
    void allows_publicHttpsTarget() {
        assertThatCode(() -> SsrfGuard.assertAllowed("https://8.8.8.8/webhook"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfGuard.assertAllowed("http://93.184.216.34/callback"))
                .doesNotThrowAnyException();
    }
}
