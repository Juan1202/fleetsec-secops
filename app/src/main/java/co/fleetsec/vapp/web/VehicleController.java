package co.fleetsec.vapp.web;

import co.fleetsec.vapp.repository.VehicleRepository;
import co.fleetsec.vapp.service.XmlParserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vehículos de la flota — <b>intencionalmente vulnerable</b>.
 *
 * <ul>
 *   <li><b>V03 · SSRF (CWE-918):</b> {@code POST /api/vehicles/{id}/webhook} hace una request
 *       server-side a la URL provista sin validar destino → alcanza IMDS (169.254.169.254)
 *       o servicios internos.</li>
 *   <li><b>V04 · XXE (CWE-611):</b> {@code POST /api/vehicles/import} delega en
 *       {@link XmlParserService} que procesa entidades externas.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private static final Logger log = LoggerFactory.getLogger(VehicleController.class);

    private final VehicleRepository vehicles;
    private final XmlParserService xmlParser;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public VehicleController(VehicleRepository vehicles, XmlParserService xmlParser) {
        this.vehicles = vehicles;
        this.xmlParser = xmlParser;
    }

    /**
     * Registra/prueba el webhook de telemetría de un vehículo.
     *
     * <p>V03: la app hace un GET server-side a {@code url} sin validar esquema ni host, y
     * devuelve la respuesta al cliente → SSRF con exfiltración de la respuesta interna.
     */
    @PostMapping("/{id}/webhook")
    public ResponseEntity<?> webhook(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url requerida"));
        }

        try {
            // ── V03 · SSRF ──────────────────────────────────────────────────────
            // Sin allowlist de host ni bloqueo de rangos privados/link-local.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Webhook probado. vehicleId={} url={} status={}", id, url, response.statusCode());
            return ResponseEntity.ok(Map.of(
                    "vehicleId", id,
                    "requestedUrl", url,
                    "status", response.statusCode(),
                    "body", response.body()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "No se pudo contactar el webhook: " + e.getMessage()));
        }
    }

    /**
     * Importa vehículos desde un documento XML.
     *
     * <p>V04: el XML se parsea con entidades externas habilitadas (ver {@link XmlParserService}).
     */
    @PostMapping(value = "/import", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<?> importXml(@RequestBody String xml) {
        try {
            String parsed = xmlParser.parse(xml);
            return ResponseEntity.ok(Map.of("parsed", parsed));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "XML inválido: " + e.getMessage()));
        }
    }
}
