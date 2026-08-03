package co.fleetsec.vapp.dto;

import co.fleetsec.vapp.domain.Driver;

/**
 * Respuesta de conductor SIN el campo {@code password} (V-05: no filtrar la credencial
 * en la respuesta del PATCH).
 */
public record DriverResponse(Long id, String username, String fullName, String cedula,
                             String email, String phone, String licenseNumber, String role) {

    public static DriverResponse from(Driver d) {
        return new DriverResponse(d.getId(), d.getUsername(), d.getFullName(), d.getCedula(),
                d.getEmail(), d.getPhone(), d.getLicenseNumber(), d.getRole());
    }
}
