# pipeline/ · DevSecOps Security Pipeline (FSEC-13)

> Pipeline de GitHub Actions con 8 stages de seguridad + commitlint sobre la app vulnerable.
> Workflow: [`.github/workflows/security.yml`](../.github/workflows/security.yml) · Spec: [`SPEC-FSEC-13-pipeline.md`](SPEC-FSEC-13-pipeline.md) · Decisión del gate: [ADR-004](../docs/ADRs/ADR-004-pipeline-gate-strategy.md).

## Los 8 stages

| # | Stage | Herramienta | Gate | SARIF |
|---|---|---|---|---|
| 2 | SAST | Semgrep (`p/java`, `p/owasp-top-ten`, `p/secrets`) + 2 custom rules | fallar en ERROR | ✅ |
| 3 | SCA | Trivy **rootfs** sobre el fat jar | fallar en CVSS≥8.0 | ✅ |
| 4 | Container | Trivy image + check tag pinneado | fallar en CRITICAL, no `:latest` | ✅ |
| 5 | IaC | Checkov (`dockerfile`,`terraform`) | fallar en check fallido | ✅ |
| 6 | DAST | OWASP ZAP autenticado (JWT) vía OpenAPI | fallar en HIGH · MEDIUM→issue | via issues |
| 7 | SBOM | Syft → CycloneDX JSON | genera artifact | n/a |
| 8 | Secrets | gitleaks-action | fallar si hay leak | ✅ |
| — | commitlint | Conventional Commits | fallar si no cumple | n/a |
| — | security-gate | agrega los 8 → required check único | fallar si algún stage falla | n/a |

Stages 2-5 + 7-8 corren en **paralelo** (jobs independientes). `security-gate` los agrega con `needs` + `if: always()`.

## Matriz de cobertura por vector

| Vector | SAST | DAST | SCA/Container | Cubierto por |
|---|---|---|---|---|
| V01 SQLi | ✅ ERROR | ✅ HIGH | — | automático |
| V04 XXE | ✅ ERROR | ❌ | — | SAST |
| V05 Mass Assignment | ✅ WARN | ❌ | — | SAST |
| V06 Path Traversal | ✅ ERROR | ❌ | — | SAST |
| V09 IDOR | ✅ WARN | ❌ | — | SAST |
| V10 Hardcoded creds | ✅ ERROR | — | — | SAST (gitleaks no lo caza — ver nota) |
| V02 JWT alg:none | ❌ | ❌ | — | **VAPT manual** (Sprint 2) |
| V03 SSRF | ❌ | ❌ | — | **VAPT manual** |
| V07 Rate limit | ❌ | ❌ | — | **VAPT manual** |
| V08 PII en logs | ❌ | ❌ | — | **VAPT manual** |
| CVEs de deps | — | — | ✅ Trivy | SCA/Container |

**Puntos ciegos del scanning automático (V02/V03/V07/V08):** lógica de negocio (forjar alg:none, SSRF a IMDS en body JSON, throttling, PII server-side en logs) que ni SAST ni DAST detectan → los cubre el pentest manual del Sprint 2. El DAST alcanza 100% de las 8 operaciones OpenAPI (active scan confirmado); no fuzzea campos de body en endpoints `Map`/`String` porque el schema queda sin properties.

**V10 y gitleaks:** gitleaks pasa verde sobre `application.yml` porque detecta secretos por firma/entropía, no secretos semánticos de config. V10 lo cubre la custom Semgrep rule `fleetsec-hardcoded-secret-in-yaml` (ERROR).

## Timing medido (run PR #6)

| | Cache-frío | Cache-warm |
|---|---|---|
| **Total wall-clock** | **3.3 min** | **1.8 min** |
| DAST (más lento) | 3.1m | 1.7m |
| Container | 2.8m | 1.1m |
| SCA | 0.8m | 1.1m |
| SAST | 0.7m | 0.5m |

Ambos **muy por debajo** del gate (≤15 min PR / ≤25 min main). Total ≈ job más lento (paralelización), no la suma.

### Optimizaciones aplicadas
- **Docker layers:** buildx + cache GHA (scope `vapp-image`) compartido entre `container` y `dast` → reusa la capa de dependencias Maven y compilación.
- **Trivy DB:** `actions/cache` de `.trivycache` montado en el contenedor Trivy.
- **Maven:** `setup-java` con `cache: maven` en `sca`, `sbom`.
- `dast` levanta con `compose --no-build` sobre la imagen ya cacheada.

## Estrategia del gate (ADR-004 · híbrido)

El pipeline es un **detector honesto**: sobre la app vulnerable sale **rojo** mostrando los vectores y CVEs reales (evidencia de detección).

- **Baseline rojo:** el run de PR #6 con SAST/SCA/Container/DAST en rojo.
- **CVEs de deps → risk-acceptance** documentada en [`.trivyignore`](../.trivyignore) (formato canónico, `Review-by` 2026-10-31): 11 CVEs≥8.0 (Tomcat, Jackson) no explotables en la superficie REST (features no habilitadas). SCA/Container → verde.
- **Vectores V01-V10 → remediación** en el VAPT (Sprint 2) con test dual. Post-remediación, `main` → **verde completo**.

## Break-glass y supresiones

- Toda supresión usa el formato canónico de 5 campos (validado por [`scripts/audit-suppressions.sh`](../scripts/audit-suppressions.sh)).
- `permissions` mínimo explícito; `issues:write` solo en el job `dast` (auto-issue MEDIUM) — menor privilegio.

## Retest local

Cada herramienta corre vía su imagen Docker oficial (ver comandos en el workflow). El SPEC documenta el detalle por stage.

Tracking: [FSEC-13](https://jandresmoya982.atlassian.net/browse/FSEC-13)
