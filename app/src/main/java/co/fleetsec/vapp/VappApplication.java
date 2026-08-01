package co.fleetsec.vapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FleetSec Vulnerable App (VAPP).
 *
 * <p>Aplicación INTENCIONALMENTE vulnerable construida para el Entregable 01 (FSEC-12).
 * Contiene 10 vectores plantados (V01-V10) que el pipeline DevSecOps (FSEC-13) debe
 * detectar y el VAPT (Sprint 2) debe explotar y remediar.
 *
 * <p><b>ADVERTENCIA:</b> no desplegar en un entorno accesible desde internet. Todos los
 * datos son sintéticos; no contiene PII real.
 */
@SpringBootApplication
public class VappApplication {

    public static void main(String[] args) {
        SpringApplication.run(VappApplication.class, args);
    }
}
