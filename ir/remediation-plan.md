# Plan de Remediación · IR-2026-001

> Derivado de las capas que fallaron en el [RCA](rca.md). **P1 ≤14 días · P2 ≤30 días · P3 ≤60 días.**
> Muchos items ya están materializados en el módulo `security-baseline` (Sprint 3) — la acción es
> **desplegarlo/enforced**, no diseñarlo desde cero.

## P1 — Crítico (≤14 días): cerrar la cadena del breach

| # | Item | Esfuerzo | Owner | Fecha objetivo | Cierra (capa RCA) |
|---|---|---|---|---|---|
| P1-1 | **Forzar IMDSv2** (`http_tokens=required`) en todas las EC2 y launch templates — mata el vector V-03/robo de creds | 2 d | SecOps | +3 d | IMDSv1→IMDSv2 |
| P1-2 | **Permission boundaries** en todos los IAM users/roles — impide la auto-escalada a admin | 3 d | SecOps | +7 d | Boundary ausente |
| P1-3 | **MFA obligatorio** para toda cuenta con acceso a consola | 2 d | SecOps + IT | +7 d | MFA no requerido |
| P1-4 | **Alerta de acceso masivo a S3** (Sigma 03 + correlación) sobre buckets de PII | 2 d | Detección | +10 d | Sin alerta de exfil |
| P1-5 | **Respuesta automatizada a GuardDuty** (EventBridge → aislar EC2 / revocar sesión) | 3 d | SecOps | +14 d | Respuesta manual tardía |
| P1-6 | **Firewall de egreso** (VPC egress allowlist / Network Firewall) — limita la exfiltración | 5 d | Redes | +14 d | Sin restricción de egreso |
| P1-7 | **Rotar todas las credenciales** potencialmente expuestas (keys, secretos, tokens) | 1 d | SecOps | +2 d | Credenciales comprometidas |

## P2 — Alto (≤30 días): endurecer y federar

| # | Item | Esfuerzo | Owner | Fecha objetivo | Cierra |
|---|---|---|---|---|---|
| P2-1 | Migrar `svc-monitoring` a **federación OIDC / IAM Roles Anywhere** (sin keys estáticas) | 2 sem | Plataforma | +30 d | Credenciales de larga vida |
| P2-2 | Reglas de detección adicionales: `CreateAccessKey`, `CreateUser`, `DisableKeyRotation` | 3 d | Detección | +21 d | Gaps de cobertura (mitre-mapping) |
| P2-3 | **Route53 Resolver DNS Firewall** para cortar el C2 por DNS (T1071.004) | 1 sem | Redes | +30 d | DNS exfil |
| P2-4 | **Tabletop exercise** sobre este mismo escenario con el equipo | 0.5 d | SecOps | +30 d | Preparación |
| P2-5 | Condición de **VPC endpoint** en la bucket policy de `prod-drivers` (acceso solo desde la VPC) | 2 d | SecOps | +25 d | Acceso a S3 sin restricción de red |

## P3 — Medio (≤60 días): proceso y gobierno

| # | Item | Esfuerzo | Owner | Fecha objetivo | Cierra |
|---|---|---|---|---|---|
| P3-1 | **Proceso de revisión periódica del baseline** de seguridad (trimestral) — la causa raíz sistémica | 1 d | GRC | +45 d | Baseline nunca revisado |
| P3-2 | Inscripción/actualización en el **RNBD** (Registro Nacional de Bases de Datos, SIC) | 2 d | Privacidad | +60 d | Cumplimiento Ley 1581 |
| P3-3 | Pipeline de **detección como código** (Sigma en CI + despliegue al SIEM) | 1 sem | Detección | +60 d | Detecciones sin gestión |
| P3-4 | Revisión de acceso (access review) trimestral de cuentas técnicas y privilegios | 2 d | GRC | +60 d | Privilegios excesivos |

## Trazabilidad P1 → módulo Terraform (Sprint 3)

| P1 | Recurso que lo materializa |
|---|---|
| P1-1 IMDSv2 | `launch_template.tf` → `http_tokens = "required"` |
| P1-2 Permission boundaries | `iam.tf` → `aws_iam_policy.permission_boundary` |
| P1-4 Alerta acceso masivo | `cloudtrail.tf` data events + Sigma `03-bulk-s3-getobject-exfil.yml` |
| P1-5 Respuesta GuardDuty | `guardduty.tf` (detector) + EventBridge (nuevo) |
| P1-6 Egreso | `vpc.tf` Flow Logs + NACL (base) → egress FW (nuevo) |

> **Total: 16 items** (7 P1 · 5 P2 · 4 P3). El P1 cierra íntegramente la cadena del breach; el P3-1
> (proceso de revisión) ataca la causa raíz **sistémica** — sin él, las capas técnicas se degradan
> con el tiempo y el incidente se vuelve a habilitar.
