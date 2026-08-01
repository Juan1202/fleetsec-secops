package co.fleetsec.vapp.domain;

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
 * Viaje de telemetría asociado a un conductor y un vehículo.
 *
 * <p>Objetivo de V09 (IDOR): {@code GET /api/drivers/{id}/trips} no verifica ownership,
 * así que cualquier usuario autenticado puede leer los viajes de otro conductor cambiando el id.
 */
@Entity
@Table(name = "trips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long driverId;

    private Long vehicleId;

    private String origin;

    private String destination;

    private Double distanceKm;

    private String startedAt;
}
