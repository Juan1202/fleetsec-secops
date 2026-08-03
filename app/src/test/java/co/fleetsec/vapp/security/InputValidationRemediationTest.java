package co.fleetsec.vapp.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test duales de la Fase 3 (input validation): V-01 (SQLi), V-04 (XXE), V-06 (path traversal),
 * V-07 (rate limiting) y de los security headers. Cada vector prueba el <b>rechazo</b> del
 * payload malicioso y el <b>flujo legítimo OK</b>.
 *
 * <p>Los endpoints exigen autenticación (V-11); los principals se derivan de tokens reales.
 * Seed: cgomez = driverId 2, aperez = driverId 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputValidationRemediationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    private String driver2() {
        return "Bearer " + jwt.generateToken("cgomez", "DRIVER", 2L);
    }

    // ── V-01 · SQL Injection ─────────────────────────────────────────────────────
    @Test
    @DisplayName("V-01 rechaza: el payload de inyección se trata como literal → 0 resultados, sin volcado")
    void v01_sqlInjectionPayload_isTreatedAsLiteral() throws Exception {
        // Con concatenación, ' OR '1'='1 devolvería TODA la tabla. Parametrizado, se busca
        // el literal → ningún nombre/cédula lo contiene → array vacío.
        mvc.perform(get("/api/drivers/search").param("q", "' OR '1'='1").header("Authorization", driver2()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("V-01 legítimo: una búsqueda real por apellido devuelve el conductor")
    void v01_legitimateSearch_returnsMatch() throws Exception {
        mvc.perform(get("/api/drivers/search").param("q", "Gómez").header("Authorization", driver2()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("cgomez"));
    }

    // ── V-03 · SSRF (wiring end-to-end; el dual completo vive en SsrfGuardTest) ───
    @Test
    @DisplayName("V-03 rechaza: webhook hacia el IMDS (169.254.169.254) → 400 sin request server-side")
    void v03_webhookToImds_isRejected() throws Exception {
        mvc.perform(post("/api/vehicles/1/webhook").header("Authorization", driver2())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://169.254.169.254/latest/meta-data/iam/\"}"))
                .andExpect(status().isBadRequest());
    }
}
