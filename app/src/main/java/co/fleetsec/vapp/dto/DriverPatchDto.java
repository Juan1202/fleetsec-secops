package co.fleetsec.vapp.dto;

/**
 * Allowlist de campos editables por PATCH (V-05 remediado). Solo {@code phone} y
 * {@code email}: {@code role} y {@code password} NO existen aquí, por lo que no son
 * asignables desde el body (raíz del mass assignment).
 */
public record DriverPatchDto(String phone, String email) {
}
