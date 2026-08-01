# CLAUDE.md · FleetSec SecOps

> Contexto permanente para Claude Code. Este archivo se lee automáticamente al iniciar sesión en el repo.
> Léelo completo antes de ejecutar cualquier tarea.

---

## Qué es este repo

Entregable de la **Prueba Técnica de Ingeniero de Ciberseguridad** para **FleetSec S.A.S.** (Quantum Data Processing Colombia) — empresa de telemetría vehicular con ~60.000 vehículos, datos personales bajo **Ley 1581 de 2012**, certificación **ISO 27001:2022** en curso.

- **Autor:** Juan Andrés Moya · Bogotá, Colombia
- **Plazo:** 7 días hábiles · **Repo:** https://github.com/Juan1202/fleetsec-secops (público)
- **Jira:** proyecto FSEC · **Confluence:** cronograma vivo

## Los 5 entregables (peso evaluativo)

| # | Entregable | Peso | Carpeta |
|---|---|---|---|
| 01 | DevSecOps Pipeline + App vulnerable | 25% | `pipeline/` `app/` |
| 02 | VAPT: 10 vulnerabilidades + remediación | 25% | `vapt/` |
| 03 | AWS Terraform Hardening | 20% | `terraform/` |
| 04 | Incident Response Playbook | 20% | `ir/` |
| 05 | Documentación + Sustentación | 10% | `docs/` |

Bonus: docker-compose (+5%), vulns extra (+5%), Conventional Commits bien hechos (+5%).

---

## ⚠️ Requisitos mandatorios (descalifican si fallan)

1. **Conventional Commits 100%.** Un solo squash commit descalifica. Cada commit debe cumplir el formato (ver abajo). El commitlint hook lo valida en local; el CI también.
2. **Video sustentación** YouTube unlisted ≤10 min con **cámara visible**. (No es tarea de código, pero afecta cómo se estructura el README.)
3. **AI Report** ≤150 palabras, 3 secciones, con **una alucinación real detectada y corregida**. Vive en `docs/ai-report.md`. Se actualiza incrementalmente en cada PR que use IA.

---

## Convenciones NO negociables

### Conventional Commits
```
<type>(<scope>): <description>
```
- **Types:** `feat`, `fix`, `fix(security)`, `docs`, `chore`, `ci`, `refactor`, `test`, `style`, `perf`, `build`, `revert`
- **Scopes:** `pipeline`, `app`, `terraform`, `vapt`, `ir`, `docs`, `infra`, `security`, `adr`, `deps`
- **Ejemplos válidos:**
  - `feat(pipeline): add ZAP DAST stage with OpenAPI coverage 80%`
  - `fix(security): remediate V-01 SQL injection in /api/drivers/search`
  - `docs(adr): add ADR-004 pipeline parallel vs sequential decision`
- Referenciar la historia Jira en el footer: `Refs: FSEC-XX`
- El `description` en minúscula, sin punto final, imperativo.

### Formato canónico de supresiones (OBLIGATORIO)
Toda entrada en `.semgrepignore`, `.trivyignore`, `checkov.yml`, `.gitleaks.toml` debe tener estos 5 campos:
```
# Rule: <ID exacto del check>
# Reason: <motivo específico, NO genérico>
# Date: YYYY-MM-DD
# Responsible: <email/handle>
# Review-by: YYYY-MM-DD (≤90 días desde Date)
```
El script `scripts/audit-suppressions.sh` valida esto. Una supresión sin los 5 campos hace fallar el CI.

### VAPT findings
- CVSSv3.1 con **vector explícito** (no solo el número), ej. `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`
- PoC reproducible (curl ejecutable o request/response con timestamp)
- CWE + OWASP Top 10 2021 + ASVS mapeados
- Impacto Ley 1581 cuando toca PII
- Cada ficha en `vapt/findings/V-XX.md`

### ADRs
- Formato Michael Nygard: Context · Decision · Consequences (positivas, negativas, neutras)
- Filename `docs/ADRs/ADR-NNN-titulo-kebab-case.md`
- Una página máximo

### Idioma
- Documentación formal en **español** (fichas VAPT, reportes, CEO briefs, SIC notifications)
- Código, comentarios técnicos, commits en inglés
- UTF-8 siempre (cédula, año, ñ)

### Fuentes oficiales versionadas
OWASP WSTG v4.2 · OWASP Top 10 2021 · CIS AWS Foundations Benchmark v1.4 · MITRE ATT&CK v14 · NIST CSF 2.0 · NIST SP 800-61 r2 · NIST SP 800-115 · ISO/IEC 27001:2022 · Ley 1581/2012 · Decreto 1377/2013

---

## Estructura del repo

```
fleetsec-secops/
├── CLAUDE.md                      ← este archivo
├── README.md                      ← principal, linkea todos los entregables
├── LICENSE                        ← MIT
├── .gitignore .gitattributes .editorconfig
├── package.json                   ← Husky + commitlint deps
├── commitlint.config.js           ← Conventional Commits + scopes
├── .gitleaks.toml                 ← extend default + allowlist documentado
├── .husky/                        ← pre-commit (gitleaks) + commit-msg (commitlint)
├── .github/
│   ├── CODEOWNERS                 ← 2 reviewers en paths sensibles
│   ├── PULL_REQUEST_TEMPLATE.md   ← checklist incl. AI disclosure
│   └── workflows/                 ← security.yml (Sprint 1) + otros
├── app/                           ← app vulnerable Spring Boot (FSEC-12)
│   ├── pom.xml Dockerfile docker-compose.yml smoke-tests.http
│   └── src/main/java/co/fleetsec/vapp/...
├── pipeline/                      ← docs del pipeline (FSEC-13)
├── vapt/                          ← findings + reports (Sprint 2)
│   ├── findings/  preflight/  reports/
├── terraform/                     ← módulo security-baseline (Sprint 3)
│   └── modules/  COMPLIANCE.md  checkov.yml
├── ir/                            ← playbook, iocs, sigma, RCA (Sprint 4)
├── docs/
│   ├── ADRs/                      ← decisiones arquitectónicas
│   ├── ai-report.md               ← MANDATORIO, incremental
│   ├── break-glass.md
│   ├── suppression-policy.md
│   └── architecture/              ← diagramas as-is/to-be (Sprint 5)
└── scripts/
    ├── setup-github.sh            ← crea repo + branch protection
    └── audit-suppressions.sh      ← valida formato canónico
```

---

## Estado actual (actualizar al avanzar)

### ✅ Sprint 0 · Fundación — CERRADO 100%
- Skills repo, cronograma Confluence, Jira FSEC (6 epics + 25 historias), repo con branch protection, AI Report template.

### 🟡 Sprint 1 · DevSecOps Pipeline — EN CURSO
- **FSEC-12** (app vulnerable Spring Boot): scaffolding listo, **pendiente merge + smoke tests**.
  - Stack: Spring Boot 3.3.5 + Java 21 + H2 + SpringDoc + Lombok
  - 10 vectores V01-V10 en 4 controllers (AuthController, DriverController, VehicleController, ReportController)
  - Dominio FleetSec: Driver (PII), Vehicle, Trip
  - Decisión en ADR-003
- **FSEC-13** (pipeline 8 stages): siguiente. Spec en `pipeline/SPEC.md`.
- **FSEC-14** (break-glass), **FSEC-15** (supresiones): cierran Sprint 1.

### Backlog
- Sprint 2 (VAPT): FSEC-16..19
- Sprint 3 (Terraform): FSEC-20..22
- Sprint 4 (IR): FSEC-23..26
- Sprint 5 (Docs): FSEC-27..31

---

## Los 10 vectores de la app (para pipeline + VAPT)

| ID | Vector | CWE | Endpoint | Archivo |
|---|---|---|---|---|
| V01 | SQL Injection | CWE-89 | `GET /api/drivers/search?q=` | DriverController.search() |
| V02 | JWT alg:none | CWE-345 | `POST /api/auth/validate` | JwtService.validateToken() |
| V03 | SSRF (IMDS) | CWE-918 | `POST /api/vehicles/{id}/webhook` | VehicleController.webhook() |
| V04 | XXE | CWE-611 | `POST /api/vehicles/import` | XmlParserService.parse() |
| V05 | Mass Assignment | CWE-915 | `PATCH /api/drivers/{id}` | DriverController.patch() |
| V06 | Path Traversal | CWE-22 | `GET /api/reports/download?file=` | ReportController.download() |
| V07 | Rate Limit ausente | CWE-307 | `POST /api/auth/login` | AuthController.login() |
| V08 | PII en logs | CWE-359 | múltiples | DriverController + logback-spring.xml |
| V09 | IDOR | CWE-639 | `GET /api/drivers/{id}/trips` | DriverController.trips() |
| V10 | Hardcoded creds | CWE-798 | config | application.yml |

---

## Quality gates (verificar antes de dar algo por hecho)

- Pipeline ≤ 15 min PR / ≤ 25 min main, 8 stages activos, 0 CRITICAL/HIGH sin supresión auditada
- VAPT: ≥7/10 con PoC, ≥8/10 remediados con **test dual** (payload rechazado + flujo legítimo OK)
- Terraform: `terraform validate` + `checkov` limpios, ≥10 controles mapeados
- IR: AWS CLI ejecutable (no pseudo-código), ≥6 técnicas MITRE ATT&CK
- Conventional Commits 100%

---

## Cómo trabajar en este repo

1. **Nunca pushear directo a `main`** — está protegido. Todo va por branch + PR.
2. **Branch naming:** `feat/fsec-XX-descripcion`, `fix/fsec-XX-descripcion`, `docs/fsec-XX-descripcion`
3. **Un PR por historia Jira** cuando sea posible.
4. **Antes de cada commit:** los hooks corren gitleaks + commitlint. Si gitleaks no está instalado en local, el hook es warn-only (el CI lo corre bloqueante).
5. **Si suprimís un gate de seguridad:** usá el formato canónico de 5 campos, sin excepción.
6. **Si usás IA para generar algo:** agregá una entrada en `docs/ai-report.md`.
7. **Cada decisión con trade-offs:** documentala en un ADR.
8. **Mostrame los diffs antes de aplicar cambios grandes** — el autor revisa parte por parte.

---

## Jira / Confluence

Las transiciones de historias en Jira y updates de Confluence las maneja el autor vía otra interfaz (conector Atlassian). Vos (Claude Code) enfocate en código + Git + PRs. Cuando cierres trabajo de una historia, mencioná el key (FSEC-XX) en el commit/PR para que el autor lo trace.
