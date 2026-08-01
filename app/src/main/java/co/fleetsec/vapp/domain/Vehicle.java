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
 * Vehículo de la flota. Cada uno pertenece a un {@code Driver} (ownerDriverId) y puede
 * registrar un webhook de telemetría — objetivo de V03 (SSRF): la URL del webhook se
 * consume sin validar destino, permitiendo alcanzar IMDS (169.254.169.254) o recursos internos.
 */
@Entity
@Table(name = "vehicles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String plate;

    private String vin;

    private String model;

    /** {@code year} es palabra reservada en SQL/H2; se mapea a la columna {@code model_year}. */
    @Column(name = "model_year")
    private Integer year;

    private Long ownerDriverId;
}
