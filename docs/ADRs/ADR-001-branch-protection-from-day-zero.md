# ADR-001 · Branch protection desde commit cero

**Status:** Accepted
**Date:** 2026-06-15
**Sprint:** 0 (Fundación)
**Authors:** Juan Andrés Moya

---

## Context

El brief de la prueba técnica enfatiza que **Conventional Commits es obligatorio** y que un único squash commit es descalificatorio. El historial Git es parte de la evidencia de proceso. La pregunta es: ¿se activa la branch protection sobre `main` **antes** o **después** del primer push de scaffolding?

### Restricciones
- Sprint 0 requiere desplegar el repo con estructura inicial (12 archivos + directorios) **en un solo commit semántico** (`chore(scaffold): initial repository scaffolding`).
- Si la branch protection con "require PR" se activa **antes** del primer push, no se podría empujar a `main` ni siquiera el commit inicial → bloqueo.
- Si la branch protection se activa **mucho después**, se pierde la disciplina sobre los commits intermedios.

### Alternativas consideradas

| Opción | Pro | Contra |
|---|---|---|
| **A) Branch protection activa desde la creación del repo** | Disciplina total | Imposible empujar el primer commit a main; obliga a setup via terraform/gh API previo, complejidad innecesaria |
| **B) Branch protection activa después del primer push de scaffolding** | Permite el bootstrap; aplica desde el segundo cambio en adelante | Pequeña ventana sin protección (segundos), aceptable porque solo el autor tiene acceso |
| **C) Crear repo con PR desde branch separada** | Branch protection ya activa | Imposible — el repo no existe todavía, no hay branch destino |
| **D) Branch protection lite (sin require PR) inicialmente** | Permite primer push | Complejidad de tener dos configuraciones diferentes; riesgo de olvidar el switch |

---

## Decision

**Opción B**: aplicar la branch protection sobre `main` **inmediatamente después** del primer push del scaffolding.

Flujo concreto:
1. `gh repo create fleetsec-secops --private --description "..."`
2. `git push -u origin main` (primer commit con todo el scaffolding)
3. **Inmediatamente** ejecutar `gh api repos/:owner/:repo/branches/main/protection -X PUT -F ...` con las reglas:
   - `required_pull_request_reviews.required_approving_review_count: 2`
   - `required_status_checks.contexts: ["security", "commitlint"]` (los contexts se agregan al final del Sprint 1 cuando el workflow existe)
   - `enforce_admins: true`
   - `allow_force_pushes: false`
   - `allow_deletions: false`
4. A partir de ese momento, **todo cambio adicional pasa por PR** con revisión.

El script `scripts/setup-github.sh` automatiza estos 3 pasos en orden seguro.

---

## Consequences

### Positivas
- ✅ El primer commit puede ser un único commit semántico limpio sin trabajo extra
- ✅ Branch protection activa inmediatamente después → toda la evidencia posterior es auditable
- ✅ Bajo riesgo: la ventana sin protección dura segundos y solo el autor tiene push access en ese momento
- ✅ El script de setup es reproducible — sirve también si hay que recrear el repo

### Negativas
- ⚠️ El commit inicial NO pasa por PR review (intencional, pero documentado aquí como excepción)
- ⚠️ Si el script de setup falla a mitad (push exitoso, branch protection no aplicada), queda un repo sin protección hasta que se complete manualmente — **mitigación**: el script idempotente puede re-correrse y el comando de branch protection es el último step

### Neutras
- 📋 Los required status checks (`security`, `commitlint`) se agregan al final del Sprint 1 — durante Sprint 0 y la mayor parte del Sprint 1 solo está activa la regla "require PR + 2 reviewers"

---

## References

- GitHub branch protection API: https://docs.github.com/en/rest/branches/branch-protection
- Conventional Commits spec: https://www.conventionalcommits.org/en/v1.0.0/
- Brief de la prueba (Quality gate: Conventional Commits 100%)

---

*Generado en Sprint 0 — primer ADR del proyecto, documenta la decisión que afecta a todos los sprints posteriores.*
