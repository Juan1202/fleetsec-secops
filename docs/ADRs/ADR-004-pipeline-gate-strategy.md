# ADR-004 · Estrategia del gate del pipeline: híbrido rojo/verde + risk-acceptance de CVEs de deps

**Status:** Accepted
**Date:** 2026-08-02
**Sprint:** 1
**Authors:** Juan Andrés Moya

---

## Context

El pipeline DevSecOps (FSEC-13) corre 8 stages sobre la app con 10 vulnerabilidades plantadas (V01-V10). Al ejecutarlo, produce hallazgos reales que hay que triar. La pregunta: **¿el pipeline debe pasar verde o rojo sobre esta app, y qué se hace con los CVEs de dependencias?**

Decisión tomada con evidencia real de dos runs CI (no a ciegas):

### Evidencia (run PR #6, cache-frío 3.3 min / cache-warm 1.8 min)

| Stage | Estado | Hallazgos reales |
|---|---|---|
| SAST (Semgrep) | 🔴 | 5 ERROR (V01, V04, V06, V10×2) + 3 WARN (V05, V09, V01 sec.) |
| SCA (Trivy rootfs) | 🔴 | 11 CVEs≥8.0 en deps (Tomcat, Jackson) |
| Container (Trivy image) | 🔴 | 4 CRITICAL (tomcat-embed-core) |
| DAST (ZAP auth) | 🔴 | 1 HIGH (V01 SQLi) + headers |
| IaC (Checkov) | 🟢 | 77/0 — Dockerfile hardened |
| Secrets · SBOM · commitlint | 🟢 | — |

### Restricciones
- El brief exige "0 CRITICAL/HIGH sin supresión auditada".
- La app TIENE los vectores a propósito; el VAPT (Sprint 2) remediará ≥8/10.
- El repo es público (ADR-002): el estado del pipeline en `main` es visible.
- Los CVEs de deps son un eje SEPARADO de los 10 vectores plantados.

### Alternativas (gate)

| Opción | Comunica | Riesgo |
|---|---|---|
| A) Verde con supresiones ya | Proceso de triage funciona | Se lee como "escondiste las vulns" |
| B) Rojo permanente en main | Máxima evidencia de detección | `main` rojo permanente se ve mal en repo público |
| **C) Híbrido** | Detección (rojo baseline) **Y** triage/remediación (verde tras Sprint 2) | Requiere coordinar timing con Sprint 2 |

---

## Decision

**Opción C (híbrido)**, con disposición diferenciada por eje:

1. **Baseline de detección (rojo):** el run de PR #6 (commit `5d8ad6c`) queda como evidencia de que la detección funciona — SAST/SCA/Container/DAST en rojo mostrando los vectores y CVEs reales. Se referencia en `pipeline/README.md`.

2. **CVEs de dependencias → risk-acceptance documentada.** Las 11 CVEs≥8.0 (Tomcat + jackson-databind) **no tienen path explotable en nuestra superficie REST** (las de Tomcat requieren HTTP/2, JSP, DIGEST auth, mTLS o RewriteValve — nada habilitado; las de jackson requieren polymorphic typing — no usado). Se suprimen en `.trivyignore` con formato canónico de 5 campos (`Review-by` ≤ 90 días) respaldadas por la tabla de triage. → SCA y Container pasan a verde.

3. **Vectores de app V01-V10 → remediación (Sprint 2).** Se remedian en el VAPT con test dual (payload rechazado + flujo legítimo OK). Hasta entonces SAST/DAST quedan rojos. Post-remediación, `main` pasa a **verde completo**.

4. **`security-gate`** agrega el resultado de todos los stages: falla si cualquier stage crítico falla. Es el único required status check para branch protection.

Resultado: `main` progresa **rojo (detección) → verde (remediación Sprint 2)**. La evidencia del rojo queda en el historial de runs.

---

## Consequences

### Positivas
- ✅ Demuestra detección real (no un pipeline decorativo) Y triage maduro (deps no explotables suprimidas con justificación).
- ✅ Cada supresión de deps linkea a la tabla de triage por explotabilidad — auditable.
- ✅ `main` verde al final = repo público presentable, tras remediación real.
- ✅ Timing 3.3 min (frío) / 1.8 min (warm), muy bajo el gate de 15 min.

### Negativas
- ⚠️ Coordinación de timing con Sprint 2: `main` queda parcialmente rojo (SAST/DAST) hasta remediar los vectores.
- ⚠️ Risk-acceptance de deps requiere revisión periódica (`Review-by` 2026-10-30) — no es un fix, es una aceptación con caducidad.

### Neutras
- 📋 Los CVEs de deps siguen en el SBOM y en Code Scanning (visibles), solo se suprime el gate. Si cambia la config (se habilita HTTP/2, typing polimórfico, etc.), la aceptación debe re-evaluarse.

---

## References

- `pipeline/SPEC-FSEC-13-pipeline.md` · spec del pipeline
- Tabla de triage de CVEs por explotabilidad (working note → se formaliza en el reporte VAPT Sprint 2)
- [ADR-002 · Repo público](ADR-002-public-repo-for-branch-protection.md)
- Runs CI: PR #6 (baseline rojo) · GitHub Actions
