# ir/ · Incident Response (Sprint 4, Entregable 04)

> Playbook completo para el breach **IR-2026-001**: compromiso de `svc-monitoring` y exfiltración
> de `prod-drivers` (PII ~60k conductores). Ciclo **NIST SP 800-61 r2**.

## Contenido

| Archivo | Historia | Qué es |
|---|---|---|
| [`iocs.md`](iocs.md) | FSEC-23 | IOCs enriquecidos (Tor `185.220.101.22`, `svc-monitoring`, EC2, `prod-drivers`) + carga al threat intel set de GuardDuty |
| [`playbook.md`](playbook.md) | FSEC-24 | 6 fases NIST con **AWS CLI ejecutable** + rollback por bloque (STS `aws:TokenIssueTime`, snapshot+memoria **antes** de stop) |
| [`detections/`](detections/) | FSEC-25 | 4 reglas Sigma (`sigma check` ✅) + SPL real de la regla de exfil |
| [`mitre-mapping.md`](mitre-mapping.md) | FSEC-25 | Matriz ATT&CK **7 técnicas** con manifestación + D3FEND + control mitigante |
| [`rca.md`](rca.md) | FSEC-26 | Root Cause Analysis (5 Whys + Swiss Cheese) — identifica **V-03 SSRF** como causa raíz |
| [`ceo-brief.md`](ceo-brief.md) | FSEC-26 | 1-pager ejecutivo (español, sin jargon) |
| [`sic-notification.md`](sic-notification.md) | FSEC-26 | Notificación SIC (7 secciones, Ley 1581 / Decreto 1377, 15 días hábiles) |
| [`remediation-plan.md`](remediation-plan.md) | FSEC-26 | Plan P1/P2/P3 (16 items) |

## El hilo de oro (VAPT → Terraform → IR)

**Sprint 2** encontró el SSRF **V-03** → **Sprint 3** lo mitiga a nivel infra (**IMDSv2**) → **Sprint 4**
(este breach) muestra qué pasa si no se mitiga y cómo responder. El [RCA](rca.md) lo hace explícito:
el V-03 permitió robar credenciales del IMDS, que es el punto de entrada del breach.

## Validación de las reglas Sigma

```bash
sigma check ir/detections/     # 0 errors, 0 condition errors (exit 0)
```

Tracking: [FSEC-23](https://jandresmoya982.atlassian.net/browse/FSEC-23) → [FSEC-26](https://jandresmoya982.atlassian.net/browse/FSEC-26)
