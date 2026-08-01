package co.fleetsec.vapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos OpenAPI. El spec queda expuesto en {@code /v3/api-docs}, que consume el DAST
 * de ZAP en el pipeline (FSEC-13) para lograr ≥80% de cobertura de endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fleetsecOpenApi() {
        return new OpenAPI().info(new Info()
                .title("FleetSec Vulnerable App API")
                .description("API intencionalmente vulnerable (V01-V10) para el pipeline DevSecOps y el VAPT.")
                .version("1.0.0"));
    }
}
