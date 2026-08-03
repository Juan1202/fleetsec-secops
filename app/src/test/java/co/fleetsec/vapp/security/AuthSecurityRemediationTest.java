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
 * Test duales de V-10 (secrets externalizados) y V-02 (JWT alg:none).
 *
 * <p>Tras la Fase 2, el endpoint {@code /api/auth/validate} fue eliminado: la validez del
 * token se prueba en el <b>path de enforcement</b> (un endpoint protegido) — más fuerte que
 * el oráculo anterior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityRemediationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    // ── V-10 · legítimo OK ──────────────────────────────────────────────────────
    @Test
    @DisplayName("V-10 legítimo: con el secret provisto, /login emite un JWT firmado")
    void v10_login_issuesSignedJwt() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-admin-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    // ── V-02 · rechaza el payload EXACTO de la PoC (V-02.md) en un endpoint protegido ──
    @Test
    @DisplayName("V-02 rechaza: el token alg:none exacto de V-02.md → 401 en endpoint protegido")
    void v02_algNoneTokenFromPoc_isRejected() throws Exception {
        String algNone = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
                + ".eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiJ9.";
        mvc.perform(get("/api/drivers/search").param("q", "x")
                        .header("Authorization", "Bearer " + algNone))
                .andExpect(status().isUnauthorized());
    }

    // ── V-02 · legítimo OK: un JWT HS256 válido autentica en un endpoint protegido ──
    @Test
    @DisplayName("V-02 legítimo: un JWT HS256 válido → 200 en endpoint protegido")
    void v02_validHs256Token_isAccepted() throws Exception {
        String token = jwt.generateToken("admin", "ADMIN", null);
        mvc.perform(get("/api/drivers/search").param("q", "x")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
