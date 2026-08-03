package co.fleetsec.vapp.web;

import co.fleetsec.vapp.domain.Driver;
import co.fleetsec.vapp.domain.Trip;
import co.fleetsec.vapp.dto.DriverPatchDto;
import co.fleetsec.vapp.dto.DriverResponse;
import co.fleetsec.vapp.repository.DriverRepository;
import co.fleetsec.vapp.repository.TripRepository;
import co.fleetsec.vapp.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conductores de la flota — <b>intencionalmente vulnerable</b>.
 *
 * <ul>
 *   <li><b>V01 · SQL Injection (CWE-89):</b> {@code GET /api/drivers/search?q=} concatena el
 *       parámetro directamente en la query vía {@link JdbcTemplate}.</li>
 *   <li><b>V05 · Mass Assignment (CWE-915):</b> {@code PATCH /api/drivers/{id}} vincula el body
 *       completo sobre la entidad, permitiendo setear {@code role=ADMIN}.</li>
 *   <li><b>V08 · Logging de PII (CWE-359 / Ley 1581):</b> se registran cédula/email/teléfono en
 *       texto plano en varios endpoints.</li>
 *   <li><b>V09 · IDOR (CWE-639):</b> {@code GET /api/drivers/{id}/trips} no verifica ownership.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    private final DriverRepository drivers;
    private final TripRepository trips;
    private final JdbcTemplate jdbc;

    public DriverController(DriverRepository drivers, TripRepository trips, JdbcTemplate jdbc) {
        this.drivers = drivers;
        this.trips = trips;
        this.jdbc = jdbc;
    }

    /**
     * Búsqueda de conductores por nombre o cédula.
     *
     * <p>V01: el parámetro {@code q} se concatena sin parametrizar → inyección SQL.
     * V08: la respuesta con PII se registra en el log en texto plano.
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        // ── V01 · SQL Injection ─────────────────────────────────────────────────
        // Concatenación directa del input del usuario en la sentencia SQL.
        String sql = "SELECT id, username, full_name, cedula, email, phone, license_number, role "
                + "FROM drivers WHERE full_name LIKE '%" + q + "%' OR cedula LIKE '%" + q + "%'";

        List<Map<String, Object>> rows = jdbc.queryForList(sql);

        // ── V08 · Logging de PII ────────────────────────────────────────────────
        // Se vuelca la query (con el payload) y la PII de los resultados sin redactar.
        log.info("Búsqueda de conductores. query=[{}] resultados={} datos={}", sql, rows.size(), rows);

        return ResponseEntity.ok(rows);
    }

    /**
     * Actualización parcial de un conductor — <b>V-05 remediado</b>.
     *
     * <p>Vincula solo campos de {@link DriverPatchDto} (allowlist: phone/email); {@code role}
     * y {@code password} no son asignables. Verifica ownership (un conductor solo edita su
     * propio registro; admin puede editar cualquiera) → 403 si no autorizado. Responde un
     * {@link DriverResponse} sin el password.
     */
    // Falso positivo de la custom rule tras V-05: la authz de ownership es por check
    // explícito (abajo), no @PreAuthorize. Verificado por EnforcementRemediationTest.
    // Ver vapt/findings/V-05.md.
    // nosemgrep: fleetsec-missing-authz-on-pathvariable-endpoint
    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id,
                                   @RequestBody DriverPatchDto body,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        Driver driver = drivers.findById(id).orElse(null);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Conductor no encontrado"));
        }
        if (!user.isAdmin() && !id.equals(user.driverId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }

        // Allowlist: solo se aplican los campos permitidos; role/password quedan intactos.
        if (body.phone() != null) {
            driver.setPhone(body.phone());
        }
        if (body.email() != null) {
            driver.setEmail(body.email());
        }
        Driver saved = drivers.save(driver);
        log.info("Conductor actualizado. id={}", saved.getId());
        return ResponseEntity.ok(DriverResponse.from(saved));
    }

    /**
     * Viajes de un conductor — <b>V-09 remediado</b>.
     *
     * <p>Verifica ownership: un conductor solo ve sus propios viajes; admin ve todos.
     * Un conductor pidiendo los viajes de otro recibe <b>403</b> (autenticado pero no autorizado).
     */
    // Falso positivo de la custom rule tras V-09: la authz de ownership es por check
    // explícito (abajo), no @PreAuthorize. Verificado por EnforcementRemediationTest.
    // Ver vapt/findings/V-09.md.
    // nosemgrep: fleetsec-missing-authz-on-pathvariable-endpoint
    @GetMapping("/{id}/trips")
    public ResponseEntity<?> trips(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        if (!user.isAdmin() && !id.equals(user.driverId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        List<Trip> result = trips.findByDriverId(id);
        log.info("Consulta de viajes. driverId={} totalViajes={}", id, result.size());
        return ResponseEntity.ok(result);
    }
}
