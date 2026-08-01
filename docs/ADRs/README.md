# Architecture Decision Records (ADRs)

> Las **decisiones arquitectónicas significativas** de FleetSec SecOps se documentan aquí en formato [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).
> Cada ADR captura: contexto · decisión · consecuencias.

## Índice

| ADR | Título | Sprint | Status |
|---|---|---|---|
| [ADR-001](ADR-001-branch-protection-from-day-zero.md) | Branch protection desde commit cero | 0 | ✅ Accepted |
| [ADR-002](ADR-002-public-repo-for-branch-protection.md) | Repositorio público para habilitar branch protection | 0 | ✅ Accepted |
| [ADR-003](ADR-003-vulnerable-app-stack.md) | Stack de la app vulnerable: Custom Spring Boot Minimal | 1 | ✅ Accepted |
| ADR-004 | Estructura del pipeline: paralelo vs secuencial + gate verde/rojo | 1 | ⏳ Proposed |
| ADR-005 | KMS CMK per service vs una sola CMK | 3 | ⏳ Proposed |
| ADR-006 | Estrategia de rate limiting: Bucket4j vs API GW vs WAF | 1 | ⏳ Proposed |
| ADR-007 | Formato de supresiones y proceso de revisión periódica | 1 | ⏳ Proposed |
| ADR-008 | Target backend Sigma: portable vs Splunk-specific | 4 | ⏳ Proposed |

## Template

Ver [`TEMPLATE.md`](TEMPLATE.md) para crear un nuevo ADR.

## Convenciones

- **Numeración secuencial** sin saltos (ADR-001, ADR-002, ...)
- **Filename**: `ADR-NNN-titulo-en-kebab-case.md`
- **Status posibles**: `Proposed` · `Accepted` · `Rejected` · `Deprecated` · `Superseded by ADR-NNN`
- **Una página máximo** — si necesita más, dividir en sub-ADRs
- **Conventional Commit** para agregar/modificar: `docs(adr): add ADR-003 KMS key-per-service decision`
