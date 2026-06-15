# Break-glass workflow · FleetSec SecOps

> **Placeholder · contenido completo se entrega en Sprint 1, Story FSEC-14.**
> Esta versión inicial documenta el contrato; la implementación con auto-Issue + regex validation va en Sprint 1.

---

## ¿Qué es break-glass?

**Break-glass** es el procedimiento que permite avanzar un PR cuando un gate de seguridad falla por una razón justificada y documentada (no como atajo silencioso).

Toda excepción a un gate CRITICAL/HIGH:
1. **Genera un Issue automáticamente** en el repo (workflow `break-glass-issue.yml` lo crea)
2. **Requiere 2 owners** del CODEOWNERS de seguridad como reviewers
3. **Solo se cierra** con un comentario que contenga `Reason: ...`, `Expires: ...`, `Approver: ...`
4. **Queda registrada permanentemente** con label `break-glass` (no se borra)

---

## Cuándo se activa

| Trigger | Severidad mínima | Workflow que crea el Issue |
|---|---|---|
| `permissions:` declarado eleva permissions vs default | CRITICAL | `break-glass-permissions.yml` |
| Suprimir hallazgo `CRITICAL` en Trivy/Semgrep/Checkov | CRITICAL | `break-glass-suppression.yml` |
| Hallazgo `HIGH` no remediable en 7 días | HIGH | manual via label `needs-break-glass` |
| Saltar stage entero del pipeline (skip job) | CRITICAL | `break-glass-skip.yml` |

---

## Implementación (Sprint 1.3)

- [ ] Workflow `.github/workflows/break-glass-issue.yml` que dispara on PR fail-status
- [ ] Regex validator en `commit-msg` o PR comment para `Reason:` / `Expires:` / `Approver:`
- [ ] Test end-to-end: introducir intencionalmente un finding y validar el flujo
- [ ] Doc final con flujo de aprobación

*A completar como parte de FSEC-14.*
