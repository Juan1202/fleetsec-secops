package co.fleetsec.vapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Test dual de V-10 (fail-fast): sin {@code app.jwt.secret} el contexto NO levanta
 * (no hay default silencioso); con el secret provisto, levanta. Usa ApplicationContextRunner
 * (contexto aislado, no carga application-test.yml) para controlar la property.
 */
class JwtSecretFailFastTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(JwtService.class);

    // ── V-10 · rechaza: sin el secret, la app no arranca ────────────────────────
    @Test
    @DisplayName("V-10 rechaza: sin app.jwt.secret el contexto FALLA (fail-fast, sin default)")
    void contextFails_whenJwtSecretMissing() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    // ── V-10 · legítimo: con el secret, el contexto levanta ─────────────────────
    @Test
    @DisplayName("V-10 legítimo: con app.jwt.secret el contexto levanta")
    void contextLoads_whenJwtSecretPresent() {
        runner.withPropertyValues("app.jwt.secret=test-only-jwt-secret-not-real-min-32-bytes-abcdef")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
