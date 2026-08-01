package co.fleetsec.vapp.repository;

import co.fleetsec.vapp.domain.Trip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByDriverId(Long driverId);
}
