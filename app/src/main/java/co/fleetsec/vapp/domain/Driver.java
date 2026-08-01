package co.fleetsec.vapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conductor de la flota FleetSec.
 *
 * <p>Contiene PII bajo Ley 1581 de 2012: {@code cedula}, {@code email}, {@code phone},
 * {@code licenseNumber}. El campo {@code role} es el objetivo de V05 (Mass Assignment):
 * un conductor no debería poder auto-asignarse {@code ADMIN} vía el body de un PATCH.
 */
@Entity
@Table(name = "drivers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** Almacenado en claro a propósito (app vulnerable); no representa práctica segura. */
    @Column(nullable = false)
    private String password;

    private String fullName;

    /** PII · Ley 1581 — cédula de ciudadanía colombiana. */
    private String cedula;

    /** PII · Ley 1581. */
    private String email;

    /** PII · Ley 1581. */
    private String phone;

    /** PII · Ley 1581 — número de licencia de conducción. */
    private String licenseNumber;

    /** Objetivo de V05 (Mass Assignment): DRIVER | ADMIN. */
    @Column(nullable = false)
    private String role;
}
