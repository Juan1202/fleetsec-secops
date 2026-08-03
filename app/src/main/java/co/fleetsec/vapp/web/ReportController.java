package co.fleetsec.vapp.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Descarga de reportes de flota — <b>intencionalmente vulnerable</b>.
 *
 * <p><b>V06 · Path Traversal (CWE-22) — REMEDIADO.</b> {@code GET /api/reports/download?file=}
 * normaliza la ruta ({@code Path.normalize()}) y exige que quede contenida en el directorio base
 * ({@code startsWith}). Un valor como {@code ../../../../etc/passwd} o una ruta absoluta se
 * rechazan con 400.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final String baseDir;

    public ReportController(@Value("${app.reports.dir:reports}") String baseDir) {
        this.baseDir = baseDir;
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam String file) {
        // ── V-06 remediado · normalización + verificación de contención ──────────
        // Se resuelve la ruta y se normaliza (colapsa ../), y se exige que quede DENTRO
        // del directorio base. Un valor como ../../../../etc/passwd escapa del base y se
        // rechaza; una ruta absoluta tampoco queda contenida.
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path resolved = base.resolve(file).normalize();

        if (!resolved.startsWith(base)) {
            log.warn("Descarga rechazada por path traversal. file=[{}]", file);
            return ResponseEntity.badRequest().body(Map.of("error", "Ruta no permitida"));
        }
        log.info("Descarga de reporte. file=[{}]", resolved.getFileName());

        try {
            if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Reporte no encontrado"));
            }
            byte[] content = Files.readAllBytes(resolved);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resolved.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo leer el archivo"));
        }
    }
}
