# ADR-003 · Stack de la app vulnerable: Custom Spring Boot Minimal

**Status:** Accepted
**Date:** 2026-06-16
**Sprint:** 1
**Authors:** Juan Andrés Moya

---

## Context

El Entregable 01 (FSEC-12) requiere una aplicación con los 10 vectores V01-V10 sobre la cual disparar el pipeline DevSecOps (FSEC-13) y conducir el VAPT (Sprint 2). El brief permite usar una app vulnerable conocida (Juice Shop, WebGoat, DVWA) o construir una propia.

La app debe: cubrir los 10 vectores exigidos, exponer un OpenAPI spec para el DAST autenticado de ZAP (cobertura ≥80%), levantar en <5 min con `docker compose`, y no consumir tiempo desproporcionado frente a los otros 4 entregables.

### Restricciones
- 7 días hábiles totales para 5 entregables; la app es medio para el pipeline/VAPT, no un fin.
- Necesita OpenAPI (`/v3/api-docs`) para ZAP.
- Debe modelar de forma creíble el dominio FleetSec (drivers con PII bajo Ley 1581, vehicles, trips).
- Control total sobre los 10 vectores (mapeo 1:1 con las custom Semgrep rules de FSEC-13).

### Alternativas consideradas

| Opción | Pro | Contra |
|---|---|---|
| A) OWASP Juice Shop (Node) | 10/10 OWASP nativo, docker listo | Muy conocido; no modela el dominio FleetSec; vectores no mapean 1:1 con el brief |
| B) WebGoat (Java/Spring) | Alineado con stack Spring | Setup pesado; lecciones didácticas, no un dominio de flota real |
| C) DVWA (PHP) | Muy didáctico | PHP no representa un stack productivo FleetSec |
| D) Custom Spring Boot **comprehensive** | Control total + realismo máximo | +8h de esfuerzo; riesgo de scope creep sobre otros entregables |
| E) **Custom Spring Boot Minimal** | **Control total de los 10 vectores; dominio FleetSec real; alineado con experiencia Spring/Java del autor; OpenAPI nativo con SpringDoc** | **Requiere construirla (mitigado: minimal, sin persistencia externa)** |

---

## Decision

**Se construye una app propia mínima: Custom Spring Boot Minimal** — Java 21 + Spring Boot 3.3.5 + H2 (en memoria) + SpringDoc (OpenAPI) + Lombok.

El stack debe **contar una historia coherente** con el dominio real de FleetSec (conductores con PII, vehículos, viajes de telemetría) y con la experiencia Spring/Java del autor, sin sacrificar tiempo de los entregables 02-05. La versión *minimal* (H2 en memoria, sin infra externa, 4 controllers) da control 1:1 sobre los 10 vectores y su mapeo con las custom Semgrep rules, evitando el scope creep de una app comprehensive.

---

## Consequences

### Positivas
- ✅ Los 10 vectores mapean 1:1 a métodos concretos (p. ej. `DriverController.trips()` para V09), lo que habilita las custom Semgrep rules de FSEC-13.
- ✅ Dominio FleetSec creíble → el impacto Ley 1581 del VAPT (PII de conductores) es tangible, no genérico.
- ✅ OpenAPI nativo vía SpringDoc en `/v3/api-docs` → ZAP logra cobertura ≥80% sin trabajo extra.
- ✅ H2 en memoria + docker-compose → `up` en <5 min, sin dependencias externas.

### Negativas
- ⚠️ Hay que construir y mantener la app (vs. clonar una existente). Mitigado por el alcance *minimal*.
- ⚠️ Al ser código propio, los vectores son responsabilidad del autor: un vector mal plantado no se dispara. Mitigado con `smoke-tests.http` que valida los 10 en cada cambio.

### Neutras
- 📋 La app queda hardened a nivel infraestructura (Dockerfile non-root, JRE alpine) pero vulnerable a nivel aplicación — separación deliberada para que el stage Container/IaC pase limpio y solo SAST/DAST reporten los vectores.

---

## References

- Brief · Entregable 01 y 02
- `pipeline/SPEC-FSEC-13-pipeline.md` (custom Semgrep rules que dependen del mapeo vector→método)
- CLAUDE.md · tabla de los 10 vectores
