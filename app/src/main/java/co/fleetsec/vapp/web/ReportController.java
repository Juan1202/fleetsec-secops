package co.fleetsec.vapp.web;

import java.io.File;
import java.nio.file.Files;
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
 * <p><b>V06 · Path Traversal (CWE-22).</b> {@code GET /api/reports/download?file=} concatena el
 * parámetro {@code file} al directorio base sin sanitizar. Un valor como
 * {@code ../../../../etc/passwd} escapa del directorio de reportes y lee archivos arbitrarios.
 *
 * <p>La remediación (Sprint 2) normalizará la ruta y verificará que quede contenida en el
 * directorio base ({@code Path.normalize()} + {@code startsWith}).
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
        // ── V06 · Path Traversal ────────────────────────────────────────────────
        // Sin normalización ni verificación de contención: el input controla la ruta.
        File target = new File(baseDir, file);
        log.info("Descarga de reporte. file=[{}] resolved=[{}]", file, target.getPath());

        try {
            if (!target.exists() || target.isDirectory()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Reporte no encontrado: " + file));
            }
            byte[] content = Files.readAllBytes(target.toPath());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + target.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo leer el archivo: " + e.getMessage()));
        }
    }
}
