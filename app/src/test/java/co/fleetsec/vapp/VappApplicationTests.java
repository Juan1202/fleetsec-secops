package co.fleetsec.vapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test baseline: verifica que el contexto de Spring levanta con el wiring actual
 * (controllers, JwtService, repositorios, H2, seed). Es el smoke test sobre el que
 * se construyen los test duales de remediación (FSEC-17).
 */
@SpringBootTest
class VappApplicationTests {

    @Test
    void contextLoads() {
        // Si el contexto no levanta (bean mal cableado, @Value sin resolver, etc.)
        // este test falla. Con la config actual debe pasar.
    }
}
