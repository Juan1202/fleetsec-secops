package co.fleetsec.vapp.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test dual de V-08 (logging de PII) sobre la lógica de redacción.
 */
class PiiMaskerTest {

    // ── Rechazo (redacción efectiva de PII) ──────────────────────────────────────
    @Test
    @DisplayName("V-08 redacta: correos y cédulas no quedan en texto plano")
    void masksEmailAndCedula() {
        String log = "Conductor cgomez cedula=1015998877 email=carlos.gomez@fleetsec.co phone=+57 3109988776";

        String masked = PiiMasker.mask(log);

        assertThat(masked).doesNotContain("1015998877");
        assertThat(masked).doesNotContain("carlos.gomez@fleetsec.co");
        assertThat(masked).doesNotContain("3109988776");
        assertThat(masked).contains("***@***");
        assertThat(masked).contains("cgomez"); // el username no es PII redactable
    }

    // ── Flujo legítimo (no rompe mensajes sin PII) ───────────────────────────────
    @Test
    @DisplayName("V-08 legítimo: un mensaje sin PII pasa intacto")
    void leavesNonPiiUntouched() {
        String log = "Búsqueda de conductores. resultados=3";

        assertThat(PiiMasker.mask(log)).isEqualTo(log);
    }

    @Test
    @DisplayName("V-08 borde: null/empty se devuelven tal cual, sin NPE")
    void handlesNullAndEmpty() {
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("")).isEmpty();
    }
}
