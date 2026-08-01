# ADR-002 · Repositorio público para habilitar branch protection

**Status:** Accepted
**Date:** 2026-06-15
**Sprint:** 0
**Authors:** Juan Andrés Moya

---

## Context

[ADR-001](ADR-001-branch-protection-from-day-zero.md) exige branch protection sobre `main` desde el commit cero (require PR, require status checks, no force push, no deletion). Sin embargo, **GitHub Free no soporta branch protection en repositorios privados**: esa capacidad requiere GitHub Pro (plan pago) o una cuenta de organización.

El repo entregable de la prueba técnica debe demostrar Conventional Commits y revisión semántica desde el inicio, lo que depende de que branch protection esté activa.

### Restricciones
- Cuenta GitHub Free (sin Pro ni organización).
- Branch protection es un requisito de ADR-001, no negociable.
- El entregable se comparte con el evaluador (requiere acceso de lectura).

### Alternativas consideradas

| Opción | Pro | Contra |
|---|---|---|
| A) Repo privado + GitHub Pro | Código no visible públicamente | Costo mensual; fuera del alcance de la prueba |
| B) Repo privado sin branch protection | Sin costo | Viola ADR-001; sin gate de PR ni status checks |
| C) Repo privado + cuenta de organización | Branch protection gratis en org | Overhead de crear/administrar una org para un entregable individual |
| D) **Repo público (Free)** | **Branch protection gratis; transparencia** | **Código visible — exige verificar que no haya PII/secretos reales** |

---

## Decision

**El repositorio `fleetsec-secops` se hace público**, habilitando branch protection sin costo bajo GitHub Free.

Esta decisión es defendible en seguridad porque:
- El código **no contiene PII real**: todos los datos (cédulas, emails, teléfonos, licencias) son sintéticos y documentados como tales.
- El único secreto en el repo es el **V10 (hardcoded credentials)**, que es **sintético e intencional** — un vector plantado para el pipeline/VAPT, no una credencial productiva válida.
- La transparencia es coherente con la postura SecOps del entregable: el pipeline, las supresiones auditadas y las remediaciones son evidencia que gana valor al ser públicamente verificable.

---

## Consequences

### Positivas
- ✅ Branch protection activa desde el commit cero sin costo (cumple ADR-001).
- ✅ El evaluador accede sin gestión de permisos.
- ✅ Coherencia narrativa: postura de seguridad transparente y auditable.

### Negativas
- ⚠️ El código es visible para cualquiera → obliga a una disciplina estricta de no introducir secretos reales. Mitigación: gitleaks en pre-commit + CI, y política de que V10 es el único secreto (sintético) tolerado.
- ⚠️ Los vectores vulnerables quedan públicos; alguien podría clonar la app y desplegarla. Mitigación: `README` y Javadoc advierten explícitamente "no desplegar en internet".

### Neutras
- 📋 Antes de cada push conviene correr gitleaks para garantizar que ningún secreto real se filtre al historial público.

---

## References

- [ADR-001 · Branch protection desde commit cero](ADR-001-branch-protection-from-day-zero.md)
- GitHub Docs · About protected branches (branch protection requiere Pro/Org en repos privados)
