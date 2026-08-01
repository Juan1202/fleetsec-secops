package co.fleetsec.vapp.config;

import co.fleetsec.vapp.domain.Driver;
import co.fleetsec.vapp.domain.Trip;
import co.fleetsec.vapp.domain.Vehicle;
import co.fleetsec.vapp.repository.DriverRepository;
import co.fleetsec.vapp.repository.TripRepository;
import co.fleetsec.vapp.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Semilla de datos sintéticos del dominio FleetSec para H2 en memoria.
 *
 * <p>Todos los datos (cédulas, emails, teléfonos, licencias) son ficticios. Existen para
 * que los 10 vectores tengan sobre qué operar: PII para V01/V08, ownership para V09,
 * un vehículo con owner para V03/V05.
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(DriverRepository drivers, VehicleRepository vehicles, TripRepository trips) {
        return args -> {
            if (drivers.count() > 0) {
                return;
            }

            Driver admin = drivers.save(Driver.builder()
                    .username("admin")
                    .password("admin123")
                    .fullName("Administrador FleetSec")
                    .cedula("79123456")
                    .email("admin@fleetsec.co")
                    .phone("+57 3001112233")
                    .licenseNumber("A-000001")
                    .role("ADMIN")
                    .build());

            Driver carlos = drivers.save(Driver.builder()
                    .username("cgomez")
                    .password("Bogota2026")
                    .fullName("Carlos Gómez")
                    .cedula("1015998877")
                    .email("carlos.gomez@fleetsec.co")
                    .phone("+57 3109988776")
                    .licenseNumber("B-104552")
                    .role("DRIVER")
                    .build());

            Driver ana = drivers.save(Driver.builder()
                    .username("aperez")
                    .password("Medellin2026")
                    .fullName("Ana Pérez")
                    .cedula("52334455")
                    .email("ana.perez@fleetsec.co")
                    .phone("+57 3157766554")
                    .licenseNumber("C-207781")
                    .role("DRIVER")
                    .build());

            Vehicle v1 = vehicles.save(Vehicle.builder()
                    .plate("ABC123")
                    .vin("9BWZZZ377VT004251")
                    .model("Chevrolet NHR")
                    .year(2021)
                    .ownerDriverId(carlos.getId())
                    .build());

            vehicles.save(Vehicle.builder()
                    .plate("XYZ789")
                    .vin("1HGCM82633A004352")
                    .model("Renault Kangoo")
                    .year(2020)
                    .ownerDriverId(ana.getId())
                    .build());

            trips.save(Trip.builder()
                    .driverId(carlos.getId())
                    .vehicleId(v1.getId())
                    .origin("Bogotá - Terminal")
                    .destination("Chía - CC Fontanar")
                    .distanceKm(28.4)
                    .startedAt("2026-06-14T08:15:00Z")
                    .build());

            trips.save(Trip.builder()
                    .driverId(ana.getId())
                    .vehicleId(v1.getId())
                    .origin("Medellín - Laureles")
                    .destination("Envigado - Centro")
                    .distanceKm(11.2)
                    .startedAt("2026-06-14T09:40:00Z")
                    .build());
        };
    }
}
