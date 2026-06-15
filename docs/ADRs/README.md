# Architecture Decision Records (ADRs)

> Las **decisiones arquitectónicas significativas** de FleetSec SecOps se documentan aquí en formato [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).
> Cada ADR captura: contexto · decisión · consecuencias.

## Índice

| ADR | Título | Sprint | Status |
|---|---|---|---|
| [ADR-001](ADR-001-branch-protection-from-day-zero.md) | Branch protection desde commit cero | 0 | ✅ Accepted |
| ADR-002 | Elección de stack para app vulnerable | 1 | ⏳ Proposed |
| ADR-003 | Estructura del pipeline: paralelo vs secuencial | 1 | ⏳ Proposed |
| ADR-004 | KMS CMK per service vs una sola CMK | 3 | ⏳ Proposed |
| ADR-005 | Estrategia de rate limiting: Bucket4j vs API GW vs WAF | 1 | ⏳ Proposed |
| ADR-006 | Formato de supresiones y proceso de revisión periódica | 1 | ⏳ Proposed |
| ADR-007 | Target backend Sigma: portable vs Splunk-specific | 4 | ⏳ Proposed |

## Template

Ver [`TEMPLATE.md`](TEMPLATE.md) para crear un nuevo ADR.

## Convenciones

- **Numeración secuencial** sin saltos (ADR-001, ADR-002, ...)
- **Filename**: `ADR-NNN-titulo-en-kebab-case.md`
- **Status posibles**: `Proposed` · `Accepted` · `Rejected` · `Deprecated` · `Superseded by ADR-NNN`
- **Una página máximo** — si necesita más, dividir en sub-ADRs
- **Conventional Commit** para agregar/modificar: `docs(adr): add ADR-003 KMS key-per-service decision`
