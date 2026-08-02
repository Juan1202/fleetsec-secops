# SPEC · FSEC-13 · Pipeline DevSecOps GitHub Actions (8 stages)

> Spec de implementación para Claude Code. Entregable 01 (25%). Historia Jira FSEC-13.
> Bloqueado por FSEC-12 (app vulnerable desplegada con OpenAPI). Bloquea FSEC-17 (remediación con tests duales).

---

## Objetivo

Pipeline de GitHub Actions que corre 8 stages de seguridad sobre la app Spring Boot vulnerable, falla ante cualquier hallazgo CRITICAL/HIGH no suprimido, sube SARIF al Security tab, genera SBOM, y termina en ≤15 min (PR) / ≤25 min (main).

## Gates inviolables

- Duración total ≤ 15 min en PR, ≤ 25 min en main
- 0 CRITICAL/HIGH sin supresión auditada (formato canónico)
- SARIF subido a GitHub Security tab (Code Scanning) desde los stages que lo soportan
- SBOM CycloneDX generado y adjuntado como artifact/release
- `permissions:` declarado explícito mínimo (NO usar el default amplio)
- Stages 2-5 corren en paralelo (jobs independientes o matrix)
- Caché configurado para acelerar (Maven deps, Trivy DB, Semgrep)

---

## Los 8 stages

| # | Stage | Herramienta | Gate | SARIF | Job |
|---|---|---|---|---|---|
| 1 | Pre-commit (local) | gitleaks hook | Local, ya existe en `.husky/` | n/a | n/a (local) |
| 2 | SAST | Semgrep + 2 custom rules | Fail HIGH/CRITICAL | ✅ | `sast` |
| 3 | SCA | Trivy fs (pom.xml deps) | Fail CVSS≥8.0 directos / ≥9.0 transitivos | ✅ | `sca` |
| 4 | Container | Trivy image | Fail CRITICAL, no `:latest` | ✅ | `container` |
| 5 | IaC | Checkov | Fail HIGH/CRITICAL | ✅ | `iac` |
| 6 | DAST | OWASP ZAP (auth) | Fail HIGH, ≥80% OpenAPI cobertura | ✅ (via issues) | `dast` |
| 7 | SBOM | Syft → CycloneDX JSON | Generado, attached | n/a | `sbom` |
| 8 | Secrets (CI) | gitleaks-action | Fail si encuentra | ✅ | `secrets` |

Además: un job `commitlint` que valida los Conventional Commits del PR.

---

## Estructura de archivos a crear

```
.github/workflows/
├── security.yml                   ← workflow principal (los 8 stages + commitlint)
pipeline/
├── SPEC.md                        ← copia de este spec (referencia)
├── README.md                      ← ya existe (placeholder), actualizar con métricas al final
├── semgrep/
│   ├── hardcoded-jwt-secret.yml   ← custom rule 1
│   └── missing-authz-check.yml    ← custom rule 2
├── zap/
│   ├── zap-auth.yaml              ← ZAP automation framework config
│   └── zap-hook.py                ← auth hook (JWT token replay)
└── .trivyignore                   ← supresiones documentadas (si aplica)
```

---

## Detalle por stage

### Stage 2 · SAST (Semgrep)

Job `sast`:
- Usa `returntocorp/semgrep` action o `semgrep ci`
- Rulesets: `p/java`, `p/owasp-top-ten`, `p/secrets` + las 2 custom rules de `pipeline/semgrep/`
- Output SARIF → upload con `github/codeql-action/upload-sarif`
- Gate: fallar si hay findings HIGH o CRITICAL (que no estén en `.semgrepignore` con formato canónico)

**Custom rule 1 · `hardcoded-jwt-secret.yml`:**
Detecta secretos JWT/passwords hardcoded en `application.yml`/`.properties`/`.java`. Debe disparar sobre V10 (el `app.jwt.secret` y `app.admin.password` en application.yml). Patrón: keys que contengan `secret`, `password`, `token` con valor literal no-placeholder.

**Custom rule 2 · `missing-authz-check.yml`:**
Detecta controllers Spring con `@GetMapping`/`@PatchMapping` que reciben `@PathVariable Long id` pero NO tienen `@PreAuthorize` ni verificación de ownership. Debe disparar sobre V09 (DriverController.trips()) y V05 (DriverController.patch()). Este es el rule que agrega valor sobre las reglas default.

### Stage 3 · SCA (Trivy filesystem)

Job `sca`:
- `aquasecurity/trivy-action` en modo `fs`, target el directorio `app/`
- Escanea `pom.xml` resuelto (correr `mvn dependency:tree` o dejar que Trivy lea el pom)
- Gate: CVSS ≥ 8.0 en deps directas, ≥ 9.0 en transitivas
- Output SARIF → upload

### Stage 4 · Container (Trivy image)

Job `container`:
- Primero build de la imagen: `docker build -t fleetsec/vulnerable-app:${{ github.sha }} app/`
- `trivy image` sobre esa tag
- Gate: fallar en CRITICAL; fallar si usa `:latest` como base
- El Dockerfile YA está hardened (non-root, alpine JRE) → debería pasar limpio a nivel infra
- Output SARIF → upload

### Stage 5 · IaC (Checkov)

Job `iac`:
- `bridgecrewio/checkov-action` sobre el repo (Dockerfile + docker-compose.yml + futuros terraform/)
- Gate: HIGH/CRITICAL
- Output SARIF → upload
- Nota: en Sprint 3 este stage también cubrirá `terraform/`

### Stage 6 · DAST (OWASP ZAP autenticado)

Job `dast` (el más pesado — cuidar el tiempo):
- Levanta la app: `docker compose -f app/docker-compose.yml up -d`, espera health
- ZAP Automation Framework (`zaproxy/action-full-scan` o `action-api-scan`)
- Importa OpenAPI desde `http://localhost:8080/v3/api-docs` → cobertura ≥80%
- Auth: el hook `zap-hook.py` hace login (POST /api/auth/login) y replica el JWT en headers
- Gate: fallar en HIGH
- **Optimización de tiempo:** en PR, correr `api-scan` (más rápido, dirigido por OpenAPI); en main (push), correr `full-scan`. Usar `if: github.event_name`.

### Stage 7 · SBOM (Syft/CycloneDX)

Job `sbom`:
- `anchore/sbom-action` con Syft → formato CycloneDX JSON
- Adjuntar como workflow artifact (`actions/upload-artifact`)
- En push a main con tag: adjuntar a release

### Stage 8 · Secrets CI (gitleaks)

Job `secrets`:
- `gitleaks/gitleaks-action`
- Usa el `.gitleaks.toml` existente (con allowlist documentado)
- Gate: fallar si encuentra secretos no allowlisted
- Nota: V10 (creds en application.yml) está en el allowlist path de vapt/, pero application.yml NO — así que este stage DEBE detectar V10 como finding esperado. Documentarlo: es un hallazgo real que el VAPT del Sprint 2 remediará.

### Job extra · commitlint

Job `commitlint`:
- `wagoid/commitlint-github-action`
- Valida todos los commits del PR contra `commitlint.config.js`
- Gate: fallar si algún commit no cumple Conventional Commits

---

## Estructura del workflow (esqueleto)

```yaml
name: DevSecOps Security Pipeline

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

# ⚠️ permissions mínimo explícito (NO default)
permissions:
  contents: read
  security-events: write   # para upload SARIF
  actions: read
  pull-requests: read

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  commitlint:
    # ...

  sast:
    # ... (paralelo)

  sca:
    # ... (paralelo)

  container:
    # ... (paralelo, necesita build de imagen)

  iac:
    # ... (paralelo)

  dast:
    # ... (secuencial después de build; el más lento)

  sbom:
    # ... (paralelo)

  secrets:
    # ... (paralelo)

  # Gate final que agrega los resultados
  security-gate:
    needs: [sast, sca, container, iac, dast, sbom, secrets, commitlint]
    runs-on: ubuntu-latest
    steps:
      - run: echo "All security gates passed"
```

---

## Caché (obligatorio para el gate de tiempo)

- Maven: `actions/cache` sobre `~/.m2/repository` con key `pom.xml` hash
- Trivy DB: `actions/cache` sobre `~/.cache/trivy`
- Semgrep: usa su propio cache o `--use-git-ignore`
- Docker layers: `docker/build-push-action` con `cache-from`/`cache-to` type=gha

---

## Criterios de aceptación (DoD)

- [ ] Workflow `security.yml` con los 8 stages + commitlint job
- [ ] `permissions:` declarado mínimo explícito
- [ ] Stages 2-5 en paralelo (jobs independientes)
- [ ] 2 custom Semgrep rules que disparan sobre V10 y V09/V05
- [ ] SARIF subido al Security tab desde SAST, SCA, Container, IaC
- [ ] ZAP corre con auth vía OpenAPI, cobertura ≥80%
- [ ] SBOM CycloneDX generado y adjuntado como artifact
- [ ] gitleaks detecta V10 como finding esperado (documentado)
- [ ] Caché configurado (Maven, Trivy, Docker layers)
- [ ] PR-mode ≤15 min, main-mode ≤25 min (medir y reportar en `pipeline/README.md`)
- [ ] El pipeline pasa VERDE sobre la app tal cual (con los findings esperados documentados como known/suppressed con formato canónico) O falla ROJO de forma controlada mostrando exactamente los vectores — decidir con el autor cuál narrativa: "verde con supresiones documentadas" vs "rojo demostrando detección". Recomendación: rojo controlado en un branch demo + verde en main con las supresiones que correspondan.

---

## Decisión pendiente para el autor (va a ADR-004)

**¿El pipeline debe pasar verde o rojo sobre la app vulnerable?**

Dos narrativas defendibles:
- **A) Verde con supresiones documentadas:** cada vector conocido está en un ignorefile con formato canónico + link a su ficha VAPT. El pipeline verde demuestra que el proceso de triage funciona. Riesgo: parece que "escondés" las vulns.
- **B) Rojo controlado:** el pipeline falla mostrando exactamente los 10 vectores. Demuestra que la detección funciona. Se acompaña de un branch/run "después de remediar" que pasa verde. Riesgo: un pipeline rojo en main se ve mal en el repo.

**Recomendación:** híbrido. Branch `demo/vulnerable-baseline` con pipeline ROJO (evidencia de detección) + `main` con las remediaciones del Sprint 2 aplicadas → VERDE. El README muestra ambos runs. Esto se documenta en ADR-004.

Claude Code: NO decidas esto solo. Preguntale al autor antes de implementar el gate final.

---

## Orden de implementación sugerido

1. Esqueleto del workflow con `permissions` + estructura de jobs vacíos + commitlint
2. Stage 8 (secrets) y Stage 2 (SAST) primero — los más rápidos de validar
3. Las 2 custom Semgrep rules
4. Stages 3, 4, 5 (SCA, Container, IaC)
5. Stage 7 (SBOM)
6. Stage 6 (DAST/ZAP) al final — el más complejo por el auth
7. Caché en todos
8. Medición de tiempo + `pipeline/README.md`
9. Consultar al autor sobre el gate final (verde vs rojo) → ADR-004

Cada paso = un commit semántico. Ejemplo:
`ci(pipeline): scaffold security workflow with minimal permissions and job structure`
`ci(pipeline): add SAST stage with Semgrep and custom rules`
etc.
