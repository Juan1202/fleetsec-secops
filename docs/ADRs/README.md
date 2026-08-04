# Architecture Decision Records (ADRs)

> Las **decisiones arquitectónicas significativas** de FleetSec SecOps se documentan aquí en formato [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).
> Cada ADR captura: contexto · decisión · consecuencias.

## Índice

| ADR | Título | Sprint | Status |
|---|---|---|---|
| [ADR-001](ADR-001-branch-protection-from-day-zero.md) | Branch protection desde commit cero | 0 | ✅ Accepted (config enmendada por ADR-009) |
| [ADR-002](ADR-002-public-repo-for-branch-protection.md) | Repositorio público para habilitar branch protection | 0 | ✅ Accepted |
| [ADR-003](ADR-003-vulnerable-app-stack.md) | Stack de la app vulnerable: Custom Spring Boot Minimal | 1 | ✅ Accepted |
| [ADR-004](ADR-004-pipeline-gate-strategy.md) | Estrategia del gate del pipeline: híbrido rojo/verde + risk-acceptance CVEs deps | 1 | ✅ Accepted |
| [ADR-009](ADR-009-relaxed-branch-protection-for-solo-context.md) | Branch protection relajada para contexto de prueba individual | 0 | ✅ Accepted |
| [ADR-010](ADR-010-jjwt-over-manual-jwt.md) | Migración de JWT manual a librería jjwt | 2 | ✅ Accepted |
| [ADR-011](ADR-011-cmk-per-service.md) | Una CMK de KMS por servicio (vs. compartida) | 3 | ✅ Accepted |

> **Nota de numeración:** los ADRs se numeran **cuando se escriben**, no se reservan. Los números 005-008 son un hueco histórico de reservas eliminadas. Ver Convenciones.

## Template

Ver [`TEMPLATE.md`](TEMPLATE.md) para crear un nuevo ADR.

## Convenciones

- **Numeración al escribir, sin reservas.** Un ADR toma el siguiente número libre **cuando se crea el archivo**; no se reservan números para temas futuros (las reservas fueron la causa de colisiones previas). Los huecos son aceptables.
- **Filename**: `ADR-NNN-titulo-en-kebab-case.md`
- **Status posibles**: `Proposed` · `Accepted` · `Rejected` · `Deprecated` · `Superseded by ADR-NNN`
- **Una página máximo** — si necesita más, dividir en sub-ADRs
- **Conventional Commit** para agregar/modificar: `docs(adr): add ADR-003 KMS key-per-service decision`
