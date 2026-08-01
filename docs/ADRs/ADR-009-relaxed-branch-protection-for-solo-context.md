# ADR-009 · Branch protection relajada para contexto de prueba individual

**Status:** Accepted
**Date:** 2026-08-01
**Sprint:** 0 (revisado en Sprint 1)
**Authors:** Juan Andrés Moya

> Enmienda la configuración de protección decidida en [ADR-001](ADR-001-branch-protection-from-day-zero.md) (2 reviewers · `enforce_admins: true`). El núcleo de ADR-001 —proteger `main` desde el commit cero, sin force-push ni deletion— se mantiene intacto.

---

## Context

[ADR-001](ADR-001-branch-protection-from-day-zero.md) definió la protección de `main` con `required_approving_review_count: 2` y `enforce_admins: true`, asumiendo un flujo de equipo con al menos dos revisores. La realidad de la prueba técnica es distinta: **un único candidato es el único colaborador del repositorio**.

Con la config de ADR-001 tal cual, **ningún PR podría mergearse**: exigir 2 aprobaciones cuando solo existe un colaborador es un deadlock, y `enforce_admins: true` impide que el propio autor (admin) desbloquee la situación. Esto paralizaría los entregables, que dependen de mergear PRs por historia Jira.

### Restricciones
- Repositorio con **un solo colaborador** (el autor) — no hay segundo revisor disponible.
- El brief exige evidencia de proceso vía PRs y Conventional Commits; los PRs deben poder **mergearse**.
- Debe preservarse la integridad del historial (Conventional Commits 100%, un squash descalifica) → force-push y deletion deben seguir bloqueados.
- La branch protection debe seguir **activa** (require PR): la evidencia de que todo pasa por PR es parte de lo evaluado.

### Alternativas consideradas

| Opción | Pro | Contra |
|---|---|---|
| A) Mantener 2 reviewers + `enforce_admins: true` (ADR-001 literal) | Máxima disciplina | Deadlock: imposible mergear sin un segundo colaborador |
| B) Invitar un colaborador ficticio/secundario para aprobar | Cumple el "2 reviewers" al pie de la letra | Artificial; introduce una cuenta sin rol real; no aporta revisión genuina |
| C) Desactivar branch protection por completo | Sin fricción | Se pierde toda la evidencia de proceso vía PR; contradice ADR-001 y el brief |
| **D) Protección activa pero relajada: 1 reviewer, `enforce_admins: false`, force-push/deletion bloqueados** | **Los PRs se pueden mergear; se conserva require-PR + integridad de historial** | **La aprobación puede ser del propio autor (self-review); menor separación de funciones** |

---

## Decision

**Opción D.** Se mantiene la branch protection sobre `main` **activa** pero con la configuración ajustada al contexto solo-candidato:

| Regla | ADR-001 | ADR-009 (vigente) |
|---|---|---|
| `required_pull_request_reviews.required_approving_review_count` | 2 | **1** |
| `enforce_admins` | `true` | **`false`** |
| `allow_force_pushes` | `false` | `false` (sin cambio) |
| `allow_deletions` | `false` | `false` (sin cambio) |
| require PR antes de merge | sí | sí (sin cambio) |
| `required_status_checks` | `["security","commitlint"]` (planeado) | pendiente hasta que exista el workflow (FSEC-13) |

La relajación es **deliberada y acotada al contexto de evaluación individual**. En un entorno productivo real de FleetSec, la config de ADR-001 (2 reviewers, `enforce_admins: true`, separación de funciones) sería la correcta y este ADR se marcaría como `Superseded`.

---

## Consequences

### Positivas
- ✅ Los PRs por historia Jira se pueden mergear sin un segundo colaborador → los entregables avanzan.
- ✅ Se conserva la evidencia de proceso: todo cambio a `main` sigue pasando por PR.
- ✅ Integridad del historial intacta: force-push y deletion siguen bloqueados → Conventional Commits 100% verificable, sin riesgo de squash accidental.

### Negativas
- ⚠️ La aprobación de PR puede ser self-review del autor → menor separación de funciones. Mitigación: cada PR incluye checklist de seguridad y los gates automáticos (gitleaks, y el pipeline de FSEC-13) sustituyen parte del control humano.
- ⚠️ `enforce_admins: false` permite al admin saltarse las reglas en una emergencia sin dejar un segundo aprobador. Mitigación: el break-glass documentado (`docs/break-glass.md`) y el historial auditado registran cualquier excepción.

### Neutras
- 📋 Config aplicable **solo** al contexto de prueba individual. En producción FleetSec se revierte a ADR-001. Este ADR deja explícito el trade-off para que el evaluador no lo lea como un descuido de seguridad.
- 📋 Los `required_status_checks` se activarán cuando exista el workflow `security.yml` (FSEC-13); hasta entonces la única regla dura es require-PR.

---

## References

- [ADR-001 · Branch protection desde commit cero](ADR-001-branch-protection-from-day-zero.md) (config que este ADR enmienda)
- [ADR-002 · Repositorio público para habilitar branch protection](ADR-002-public-repo-for-branch-protection.md)
- `docs/break-glass.md` · procedimiento de excepción auditado
- GitHub branch protection API: https://docs.github.com/en/rest/branches/branch-protection
- Estado real verificado (`gh api .../branches/main/protection`, 2026-08-01): 1 reviewer · `enforce_admins: false` · force-push/deletion bloqueados
