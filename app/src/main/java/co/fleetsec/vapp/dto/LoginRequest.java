package co.fleetsec.vapp.dto;

/** Credenciales de login (V07 · endpoint sin rate limiting). */
public record LoginRequest(String username, String password) {
}
