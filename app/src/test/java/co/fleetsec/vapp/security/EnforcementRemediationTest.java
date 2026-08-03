package co.fleetsec.vapp.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Test duales de V-11 (enforcement), V-05 (mass assignment), V-09 (IDOR) y del
 * <b>análisis de interacción</b>: las dos vías al account takeover quedan cerradas.
 *
 * <p>Los principals se derivan de tokens reales (pasan por el filtro JWT). Datos del seed:
 * cgomez = driverId 2 (tiene viajes), aperez = driverId 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnforcementRemediationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    private String driver2() {
        return "Bearer " + jwt.generateToken("cgomez", "DRIVER", 2L);
    }

    private String driver3() {
        return "Bearer " + jwt.generateToken("aperez", "DRIVER", 3L);
    }

    // ── V-11 · enforcement ──────────────────────────────────────────────────────
    @Test
    @DisplayName("V-11 rechaza: sin token, un endpoint protegido → 401")
    void v11_noToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/drivers/search").param("q", "Ana"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V-11 legítimo: con token válido → 200")
    void v11_withToken_isOk() throws Exception {
        mvc.perform(get("/api/drivers/search").param("q", "Ana").header("Authorization", driver2()))
                .andExpect(status().isOk());
    }

    // ── V-05 · mass assignment ──────────────────────────────────────────────────
    @Test
    @DisplayName("V-05 rechaza: role/password NO asignables por el body; sin password en la respuesta")
    void v05_roleAndPasswordNotAssignable() throws Exception {
        mvc.perform(patch("/api/drivers/2").header("Authorization", driver2())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+57 3000000000\",\"role\":\"ADMIN\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"))          // role NO cambió
                .andExpect(jsonPath("$.phone").value("+57 3000000000")) // phone SÍ (allowlist)
                .andExpect(jsonPath("$.password").doesNotExist());      // password NO filtrado
    }

    // ── V-09 · IDOR (403 = autenticado pero no autorizado) ──────────────────────
    @Test
    @DisplayName("V-09 rechaza: driver A pidiendo los viajes de B → 403")
    void v09_otherDriverTrips_isForbidden() throws Exception {
        mvc.perform(get("/api/drivers/2/trips").header("Authorization", driver3())) // aperez(3) pide de cgomez(2)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("V-09 legítimo: un driver pidiendo sus propios viajes → 200")
    void v09_ownTrips_isOk() throws Exception {
        mvc.perform(get("/api/drivers/2/trips").header("Authorization", driver2())) // cgomez(2) los suyos
                .andExpect(status().isOk());
    }

    // ── Interacción · cierre de las DOS vías al account takeover ────────────────

    @Test
    @DisplayName("Interacción (vía 1a): un token alg:none forjado NO autentica → 401")
    void interaction_forgedAlgNone_cannotAuthenticate() throws Exception {
        String algNone = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
                + ".eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiJ9.";
        mvc.perform(get("/api/drivers/search").param("q", "x").header("Authorization", "Bearer " + algNone))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Interacción (vía 1b): un token firmado con OTRO secret (viejo/rotado) → 401")
    void interaction_wrongSecretToken_cannotAuthenticate() throws Exception {
        // Representa el secret viejo/filtrado tras la rotación: cualquier clave distinta
        // a la de la app produce una firma que el filtro rechaza.
        JwtService wrongKey = new JwtService("old-leaked-secret-rotated-away-not-the-app-key-32b");
        String forged = wrongKey.generateToken("admin", "ADMIN", null);
        mvc.perform(get("/api/drivers/search").param("q", "x").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Interacción (vía 2): un driver autenticado NO puede auto-promoverse a admin (V-05)")
    void interaction_driverCannotSelfPromoteToAdmin() throws Exception {
        mvc.perform(patch("/api/drivers/2").header("Authorization", driver2())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER")); // sigue DRIVER: sin escalada
    }
}
