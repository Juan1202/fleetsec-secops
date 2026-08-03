package co.fleetsec.vapp.security;

/**
 * Principal autenticado, extraído del JWT por {@link JwtAuthFilter}. Expone el
 * {@code driverId} para las verificaciones de ownership (V-05, V-09). Es null para
 * el admin de configuración (que no tiene registro de conductor).
 */
public record AuthenticatedUser(String username, String role, Long driverId) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
