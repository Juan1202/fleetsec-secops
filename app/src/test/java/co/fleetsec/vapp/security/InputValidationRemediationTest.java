package co.fleetsec.vapp.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @DisplayName("V-01 legítimo: una búsqueda real por nombre devuelve el conductor")
    void v01_legitimateSearch_returnsMatch() throws Exception {
        // H2 devuelve las claves de columna en MAYÚSCULA (USERNAME).
        mvc.perform(get("/api/drivers/search").param("q", "Carlos").header("Authorization", driver2()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].USERNAME").value("cgomez"));
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

    // ── V-04 · XXE ───────────────────────────────────────────────────────────────
    @Test
    @DisplayName("V-04 rechaza: un XML con DOCTYPE/entidad externa → 400 (parser rechaza el DOCTYPE)")
    void v04_xxePayload_isRejected() throws Exception {
        String xxe = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>"
                + "<vehicles>&xxe;</vehicles>";
        mvc.perform(post("/api/vehicles/import").header("Authorization", driver2())
                        .contentType(MediaType.APPLICATION_XML).content(xxe))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("V-04 legítimo: un XML sin DOCTYPE se parsea correctamente → 200")
    void v04_cleanXml_isParsed() throws Exception {
        String xml = "<?xml version=\"1.0\"?><vehicles><vehicle>ABC123</vehicle></vehicles>";
        mvc.perform(post("/api/vehicles/import").header("Authorization", driver2())
                        .contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsed").value("ABC123"));
    }

    // ── V-06 · Path Traversal ─────────────────────────────────────────────────────
    @Test
    @DisplayName("V-06 rechaza: file=../../../../etc/passwd escapa del base → 400")
    void v06_pathTraversal_isRejected() throws Exception {
        mvc.perform(get("/api/reports/download").param("file", "../../../../etc/passwd")
                        .header("Authorization", driver2()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("V-06 legítimo: un reporte dentro del directorio base se descarga → 200")
    void v06_legitimateReport_isDownloaded() throws Exception {
        mvc.perform(get("/api/reports/download").param("file", "reporte-flota-2026-06.txt")
                        .header("Authorization", driver2()))
                .andExpect(status().isOk());
    }

    // ── V-07 · Rate limiting (IPs distintas por test para aislar el limiter singleton) ──
    @Test
    @DisplayName("V-07 rechaza: superar el cupo (5/min) desde una IP → el 6º intento → 429")
    void v07_bruteForce_isRateLimited() throws Exception {
        String badLogin = "{\"username\":\"attacker\",\"password\":\"wrong\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("9.9.9.9"); return r; })
                            .contentType(MediaType.APPLICATION_JSON).content(badLogin))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("9.9.9.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(badLogin))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("V-07 legítimo: un login válido desde otra IP dentro del cupo → 200 con token")
    void v07_legitimateLogin_isAllowed() throws Exception {
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("8.8.4.4"); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-admin-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    // ── Security headers (defensa en profundidad; presentes incluso en un 401) ────
    @Test
    @DisplayName("Headers: toda respuesta lleva X-Content-Type-Options, X-Frame-Options, CSP, Referrer/Permissions-Policy")
    void securityHeaders_arePresent() throws Exception {
        mvc.perform(get("/api/drivers/search").param("q", "x")) // sin token → 401, pero con headers
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Permissions-Policy"));
    }
}
