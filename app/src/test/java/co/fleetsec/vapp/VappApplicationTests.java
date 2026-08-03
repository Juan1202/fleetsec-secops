package co.fleetsec.vapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test baseline: verifica que el contexto de Spring levanta con el wiring actual.
 * Usa el perfil {@code test} (application-test.yml), que provee los secretos de test
 * — tras V-10 (secretos externalizados) el contexto no levanta sin ellos.
 */
@SpringBootTest
@ActiveProfiles("test")
class VappApplicationTests {

    @Test
    void contextLoads() {
        // Falla si el contexto no levanta (bean mal cableado, @Value sin resolver, etc.).
    }
}
