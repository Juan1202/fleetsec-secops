# app/ · FleetSec Vulnerable App (VAPP)

> App **intencionalmente vulnerable** para el Sprint 1 — Entregable 01 (DevSecOps Pipeline).
> Stack decidido en [ADR-003](../docs/ADRs/ADR-003-vulnerable-app-stack.md): **Custom Spring Boot Minimal**.
> Historia Jira: [FSEC-12](https://jandresmoya982.atlassian.net/browse/FSEC-12).

> ⚠️ **NO desplegar en un entorno accesible desde internet.** Contiene 10 vulnerabilidades
> plantadas a propósito. Todos los datos son sintéticos; no hay PII real.

## Stack

| Componente | Versión |
|---|---|
| Java | 21 (Temurin) |
| Spring Boot | 3.3.5 |
| Base de datos | H2 en memoria |
| OpenAPI | SpringDoc 2.6.0 (`/v3/api-docs`, `/swagger-ui.html`) |
| Boilerplate | Lombok |

## Cómo levantarla

Requiere Docker (no requiere Maven ni JDK local — el build corre en el stage builder del Dockerfile):

```bash
docker compose -f app/docker-compose.yml up -d --build
```

- App: http://localhost:8080
- OpenAPI spec (para ZAP): http://localhost:8080/v3/api-docs
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 console: http://localhost:8080/h2-console (jdbc:h2:mem:fleetsec, user `sa`, sin password)

Parar y limpiar:

```bash
docker compose -f app/docker-compose.yml down
```

## Los 10 vectores (V01-V10)

Todos verificados en vivo con [`smoke-tests.http`](smoke-tests.http):

| ID | Vector | CWE | Endpoint | Archivo |
|---|---|---|---|---|
| V01 | SQL Injection | CWE-89 | `GET /api/drivers/search?q=` | `DriverController.search()` |
| V02 | JWT alg:none | CWE-345 | `POST /api/auth/validate` | `JwtService.validateToken()` |
| V03 | SSRF (IMDS) | CWE-918 | `POST /api/vehicles/{id}/webhook` | `VehicleController.webhook()` |
| V04 | XXE | CWE-611 | `POST /api/vehicles/import` | `XmlParserService.parse()` |
| V05 | Mass Assignment | CWE-915 | `PATCH /api/drivers/{id}` | `DriverController.patch()` |
| V06 | Path Traversal | CWE-22 | `GET /api/reports/download?file=` | `ReportController.download()` |
| V07 | Missing Rate Limiting | CWE-307 | `POST /api/auth/login` | `AuthController.login()` |
| V08 | Logging de PII (Ley 1581) | CWE-359 | múltiples | `DriverController` + `logback-spring.xml` |
| V09 | IDOR | CWE-639 | `GET /api/drivers/{id}/trips` | `DriverController.trips()` |
| V10 | Hardcoded Credentials | CWE-798 | config | `application.yml` |

## Diseño de seguridad (deliberado)

La app es vulnerable **a nivel de aplicación** (V01-V10) pero está hardened **a nivel de
infraestructura**: el `Dockerfile` usa JRE alpine con tag pinneado, usuario non-root y
healthcheck. Así, el stage Container/IaC del pipeline (FSEC-13) pasa limpio y solo los
stages SAST/DAST reportan los vectores de aplicación.

## Estructura

```
app/
├── pom.xml
├── Dockerfile                 multi-stage: Maven builder → JRE alpine non-root
├── docker-compose.yml
├── smoke-tests.http           10 requests, uno por vector (+ baselines)
├── reports/                   directorio base de la path traversal (V06)
└── src/main/
    ├── java/co/fleetsec/vapp/
    │   ├── VappApplication.java
    │   ├── config/            OpenApiConfig · DataSeeder
    │   ├── domain/            Driver (PII) · Vehicle · Trip
    │   ├── repository/        DriverRepository · VehicleRepository · TripRepository
    │   ├── security/          JwtService            (V02)
    │   ├── service/           XmlParserService      (V04)
    │   └── web/               AuthController · DriverController · VehicleController · ReportController
    └── resources/
        ├── application.yml     (V10)
        └── logback-spring.xml  (V08)
```
