package co.fleetsec.vapp.repository;

import co.fleetsec.vapp.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
}
