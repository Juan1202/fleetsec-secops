package co.fleetsec.vapp.security;

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
 * Test duales de remediación de V-10 (secrets externalizados) y V-02 (JWT alg:none).
 * Perfil {@code test}: los secretos vienen de application-test.yml.
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

    // ── V-02 · rechaza el payload EXACTO de la PoC (V-02.md) ────────────────────
    @Test
    @DisplayName("V-02 rechaza: el token alg:none exacto de V-02.md → 401 valid:false")
    void v02_algNoneTokenFromPoc_isRejected() throws Exception {
        // header {"alg":"none","typ":"JWT"} . payload {"sub":"admin","role":"ADMIN"} . (sin firma)
        String algNone = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
                + ".eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiJ9.";
        mvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + algNone + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    // ── V-02 · legítimo OK (el fix no rompe el login legítimo) ──────────────────
    @Test
    @DisplayName("V-02 legítimo: un JWT HS256 válido emitido por la app → valid:true")
    void v02_validHs256Token_isAccepted() throws Exception {
        String token = jwt.generateToken("admin", "ADMIN");
        mvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.sub").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
