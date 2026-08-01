package co.fleetsec.vapp.web;

import co.fleetsec.vapp.domain.Driver;
import co.fleetsec.vapp.dto.LoginRequest;
import co.fleetsec.vapp.repository.DriverRepository;
import co.fleetsec.vapp.security.JwtService;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación de la flota.
 *
 * <ul>
 *   <li><b>V07 · Missing Rate Limiting (CWE-307):</b> {@code POST /api/auth/login} no aplica
 *       throttling ni bloqueo por intentos fallidos → brute force / credential stuffing.</li>
 *   <li><b>V02 · JWT alg:none (CWE-345):</b> {@code POST /api/auth/validate} delega en
 *       {@link JwtService#validateToken} que acepta {@code alg:none}.</li>
 *   <li><b>V10 · Hardcoded Credentials (CWE-798):</b> las credenciales de admin provienen de
 *       {@code app.admin.*} hardcoded en application.yml.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DriverRepository drivers;
    private final JwtService jwt;
    private final String adminUser;
    private final String adminPassword;

    public AuthController(DriverRepository drivers,
                          JwtService jwt,
                          @Value("${app.admin.username}") String adminUser,
                          @Value("${app.admin.password}") String adminPassword) {
        this.drivers = drivers;
        this.jwt = jwt;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    /**
     * Login sin rate limiting (V07). Devuelve un JWT si las credenciales son válidas.
     * El admin se autentica contra las credenciales hardcoded (V10).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        // V10: credencial de admin hardcoded en config, usada aquí.
        if (adminUser.equals(req.username()) && adminPassword.equals(req.password())) {
            return ResponseEntity.ok(Map.of(
                    "token", jwt.generateToken(adminUser, "ADMIN"),
                    "role", "ADMIN"));
        }

        Optional<Driver> driver = drivers.findByUsername(req.username());
        if (driver.isPresent() && driver.get().getPassword().equals(req.password())) {
            Driver d = driver.get();
            return ResponseEntity.ok(Map.of(
                    "token", jwt.generateToken(d.getUsername(), d.getRole()),
                    "role", d.getRole()));
        }

        // V07: mismo mensaje, sin backoff ni lockout → permite fuerza bruta.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales inválidas"));
    }

    /**
     * Valida un token y devuelve la identidad reclamada (V02).
     * Un token forjado con {@code alg:none} es aceptado tal cual.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "token requerido"));
        }
        try {
            JwtService.TokenClaims claims = jwt.validateToken(token);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "sub", String.valueOf(claims.sub()),
                    "role", String.valueOf(claims.role())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", e.getMessage()));
        }
    }
}
