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
 * <p>El endpoint {@code POST /api/auth/validate} fue <b>eliminado</b> en FSEC-17:
 * post-remediación era redundante (el filtro JWT valida el token en cada request
 * protegido) y ser un oráculo de tokens es superficie de ataque innecesaria.
 *
 * <p>V-07 (rate limiting) se remedia en la Fase 3.
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
     * Login. Devuelve un JWT firmado si las credenciales son válidas. El admin de
     * configuración no tiene {@code driverId} (null); un conductor lleva su id en el token
     * para las verificaciones de ownership (V-05, V-09).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (adminUser.equals(req.username()) && adminPassword.equals(req.password())) {
            return ResponseEntity.ok(Map.of(
                    "token", jwt.generateToken(adminUser, "ADMIN", null),
                    "role", "ADMIN"));
        }

        Optional<Driver> driver = drivers.findByUsername(req.username());
        if (driver.isPresent() && driver.get().getPassword().equals(req.password())) {
            Driver d = driver.get();
            return ResponseEntity.ok(Map.of(
                    "token", jwt.generateToken(d.getUsername(), d.getRole(), d.getId()),
                    "role", d.getRole()));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales inválidas"));
    }
}
