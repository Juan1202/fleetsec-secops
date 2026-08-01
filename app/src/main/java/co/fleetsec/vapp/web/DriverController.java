package co.fleetsec.vapp.web;

import co.fleetsec.vapp.domain.Driver;
import co.fleetsec.vapp.domain.Trip;
import co.fleetsec.vapp.repository.DriverRepository;
import co.fleetsec.vapp.repository.TripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final ObjectMapper mapper = new ObjectMapper();

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
     * Actualización parcial de un conductor.
     *
     * <p>V05: se vincula el body completo sobre la entidad persistida vía
     * {@link ObjectMapper#updateValue}, incluidos campos sensibles como {@code role} y
     * {@code password} que no deberían ser editables por el propio conductor.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Driver driver = drivers.findById(id).orElse(null);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Conductor no encontrado"));
        }

        try {
            // ── V05 · Mass Assignment ───────────────────────────────────────────
            // updateValue mergea TODAS las claves del body sobre la entidad, sin allowlist.
            Driver merged = mapper.updateValue(driver, updates);
            Driver saved = drivers.save(merged);

            // V08: se registra el cambio con PII y el nuevo rol en claro.
            log.info("Conductor actualizado. id={} cedula={} email={} nuevoRole={}",
                    saved.getId(), saved.getCedula(), saved.getEmail(), saved.getRole());

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Body inválido: " + e.getMessage()));
        }
    }

    /**
     * Viajes de un conductor.
     *
     * <p>V09 · IDOR: recibe {@code id} por path y devuelve sus viajes sin comprobar que el
     * solicitante sea el dueño ni un admin. Cambiar el id expone viajes de otros conductores.
     */
    @GetMapping("/{id}/trips")
    public ResponseEntity<?> trips(@PathVariable Long id) {
        List<Trip> result = trips.findByDriverId(id);
        log.info("Consulta de viajes. driverId={} totalViajes={}", id, result.size());
        return ResponseEntity.ok(result);
    }
}
