# VAPT · FleetSec Vulnerable App

> Análisis de vulnerabilidades (Sprint 2 · Entregable 02). 10 vectores del brief + 1 bonus, todos con **PoC ejecutado** sobre el entorno local.
> Fichas: [`findings/V-XX.md`](findings/) · Evidencia de disparo: [`preflight/`](preflight/) · Remediación: FSEC-17 · Reporte ejecutivo: FSEC-18.

## Resumen de hallazgos

| ID | Vector | CWE | Severidad | CVSS | Estado |
|----|--------|-----|-----------|------|--------|
| [V-03](findings/V-03.md) | SSRF | CWE-918 | 🟠 HIGH | **8.6** | Open |
| [V-11](findings/V-11.md) | Missing Auth Enforcement _(bonus)_ | CWE-306 | 🟠 HIGH | **8.2** | Open |
| [V-05](findings/V-05.md) | Mass Assignment | CWE-915 | 🟠 HIGH | **8.1** | Open |
| [V-01](findings/V-01.md) | SQL Injection | CWE-89 | 🟠 HIGH | **7.5** | Open |
| [V-04](findings/V-04.md) | XXE | CWE-611 | 🟠 HIGH | **7.5** | Open |
| [V-06](findings/V-06.md) | Path Traversal | CWE-22 | 🟠 HIGH | **7.5** | Open |
| [V-10](findings/V-10.md) | Hardcoded Credentials | CWE-798 | 🟠 HIGH | **7.5** | Open |
| [V-02](findings/V-02.md) | JWT alg:none | CWE-347 | 🟡 MEDIUM | **6.5** | Open |
| [V-08](findings/V-08.md) | PII en logs (Ley 1581) | CWE-359 | 🟡 MEDIUM | **6.1** | Open |
| [V-09](findings/V-09.md) | IDOR | CWE-639 | 🟡 MEDIUM | **5.3** | Open |
| [V-07](findings/V-07.md) | Missing Rate Limiting | CWE-307 | 🟡 MEDIUM | **5.3** | Open |

**Distribución:** 0 CRITICAL · **7 HIGH** · 4 MEDIUM · **11 con PoC** (supera el gate ≥7/10).
La ausencia de CRITICAL es deliberada: cada score se calculó sobre el **impacto demostrado en el sistema real** (p. ej. V-01 quedó HIGH y no CRITICAL porque el sink `queryForList` **bloquea** el stacked-query→RCE, verificado), no sobre el potencial teórico del CWE.

---

## 🔗 Cadenas de interacción entre vulnerabilidades

> **El diferenciador del análisis: las vulns no son aisladas — se potencian.** Esto define el **orden de remediación** (ver FSEC-18).

| # | Cadena | Mecanismo | Consecuencia |
|---|--------|-----------|--------------|
| 1 | **V-11 → V-02 + V-10** | Hoy V-11 (sin enforcement) **acota** el impacto de V-02 (JWT alg:none) y V-10 (secrets): los tokens no gatean nada. **Remediar solo V-11** activa el enforcement sobre un mecanismo de auth roto. | ⏰ **Account takeover** (forja admin → C:H/I:H ~9.1). **Orden:** arreglar V-02 + V-10 **antes o junto con** V-11. |
| 2 | **V-07 + V-10** | Login sin lockout (V-07) + contraseñas débiles/conocidas (V-10: `admin123`, `Bogota2026`). | Fuerza bruta trivialmente exitosa. |
| 3 | **V-03 → IMDS** | SSRF (V-03) alcanza `169.254.169.254` en el entorno AWS de FleetSec. | Robo de credenciales IAM → **pivot a la cuenta cloud** (encadena con el breach del Entregable 04). |
| 4 | **V-01 / V-04 / V-06 → V-10** | Los tres vectores de **lectura de archivos** (SQLi `FILE_READ`, XXE, path traversal) pueden leer `application.yml`. | Exfiltración de los **secrets hardcoded** (V-10) → cierra el círculo hacia forja de tokens. |

---

## Mapa de superficie de ataque

| Endpoint | Método | Auth requerida | Auth enforceada | Estado | Findings |
|----------|--------|:---:|:---:|:---:|----------|
| `/api/auth/login` | POST | Sí | — | 🔴 Vulnerable | V-07, V-10 |
| `/api/auth/validate` | POST | Sí | ❌ (V-02) | 🔴 Vulnerable | V-02 |
| `/api/drivers/search` | GET | Sí | ❌ (V-11) | 🔴 Vulnerable | V-01, V-08, V-11 |
| `/api/drivers/{id}` | PATCH | Sí | ❌ (V-11) | 🔴 Vulnerable | V-05, V-11 |
| `/api/drivers/{id}/trips` | GET | Sí | ❌ (V-11) | 🔴 Vulnerable | V-09, V-11 |
| `/api/vehicles/{id}/webhook` | POST | Sí | ❌ (V-11) | 🔴 Vulnerable | V-03, V-11 |
| `/api/vehicles/import` | POST | Sí | ❌ (V-11) | 🔴 Vulnerable | V-04, V-11 |
| `/api/reports/download` | GET | Sí | ❌ (V-11) | 🔴 Vulnerable | V-06, V-11 |

Todos los endpoints deberían exigir autenticación; **ninguno la enforcea** (V-11).

---

## Coherencia con el pipeline (Entregable 01)

| Detección | Vectores |
|---|---|
| **Doble fuente** (pipeline + PoC manual) | V-01 (SAST+DAST), V-04/V-05/V-06/V-09/V-10 (SAST) |
| **Solo pentest manual** (el scanner no lo caza — documentado en cada ficha) | V-02 (JWT manual), V-03 (URL en body), V-07 (control negativo), V-08 (server-side) |

Nota de coherencia: V-11 aparece como `WARNING` parcial en SAST (`missing-authz`, 2 endpoints) pero se scorea HIGH en la ficha por ser un problema **arquitectural global** — la herramienta ve una muestra, el pentest ve el sistema.

## Remediación

Todos los hallazgos están **Open**. La remediación (≥8/10 con test dual) se ejecuta en **FSEC-17**; el reporte ejecutivo + técnico y el análisis de interacción en **FSEC-18**.

## Estructura

- `findings/V-XX.md` — una ficha por vector (CWE · OWASP · ASVS · CVSSv3.1 vector · PoC · Impact · Remediation)
- `preflight/` — evidencia de que cada vector dispara pre-remediación
- `reports/` — DOCX + PDF del reporte ejecutivo + técnico (FSEC-18)
- `tests/` — tests duales por remediación (FSEC-17)

Tracking: [FSEC-16](https://jandresmoya982.atlassian.net/browse/FSEC-16) → [FSEC-19](https://jandresmoya982.atlassian.net/browse/FSEC-19)
