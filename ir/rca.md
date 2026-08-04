# Root Cause Analysis · IR-2026-001

> Post-mortem **blameless**. Método: 5 Whys + modelo Swiss Cheese. **Hipótesis central: la causa
> raíz técnica es el SSRF V-03 (Sprint 2), que permitió robar credenciales del IMDS.**
> (Punto de decisión #3 — el autor valida la cadena causal antes de darla por establecida.)

## Resumen

El 2026-07-28, entre las 02:00 y 04:00 UTC, un actor comprometió las credenciales de
`svc-monitoring`, escaló a `AdministratorAccess`, exfiltró ~45.7 GB de PII del bucket
`prod-drivers` (~60.000 conductores) e intentó borrar CloudTrail (bloqueado por SCP). El acceso
se originó desde un nodo Tor (`185.220.101.22`).

## 5 Whys

1. **¿Por qué se exfiltraron 45.7 GB de PII?**
   Porque el actor tenía `AdministratorAccess` sobre `svc-monitoring` y el bucket no restringía el
   acceso por VPC endpoint ni alertaba sobre lectura masiva.

2. **¿Por qué `svc-monitoring` obtuvo `AdministratorAccess`?**
   Porque `AttachUserPolicy` tuvo éxito: **no existía un permission boundary** que acotara el techo
   de permisos del usuario. Sin boundary, adjuntar admin surte efecto pleno.

3. **¿Por qué el actor pudo autenticarse como `svc-monitoring`?**
   Porque **robó las credenciales del rol de la instancia desde el IMDS** (`169.254.169.254`),
   explotando el **SSRF V-03** de la aplicación (hallazgo del Sprint 2).

4. **¿Por qué el SSRF alcanzó el IMDS y obtuvo credenciales?**
   Porque la EC2 tenía **IMDSv1 habilitado** (sin `http_tokens=required`): IMDSv1 responde a un
   simple GET server-side, sin el token de sesión que IMDSv2 exige — justo lo que un SSRF puede hacer.

5. **¿Por qué no se detectó y respondió antes?**
   Porque los findings de GuardDuty **no tenían respuesta automática**; la detección existía pero la
   reacción fue manual y tardía (~2h hasta el finding de DNS exfil).

> **Causa raíz técnica:** el SSRF **V-03** habilitó el robo de credenciales vía IMDSv1.
> **Causa raíz sistémica:** ausencia de un baseline de seguridad enforced (IMDSv2, permission
> boundaries, alertas de acceso masivo) y de un proceso de respuesta automatizada — es decir, el
> problema no es una sola policy faltante, sino que el baseline nunca fue enforced end-to-end.

## Modelo Swiss Cheese — capas que debieron detener el ataque

| Capa | ¿Detuvo? | Por qué falló / funcionó | Mitigación (Sprint 3) |
|---|---|---|---|
| IMDSv2 obligatorio | ❌ | IMDSv1 habilitado → el SSRF robó las creds | `launch_template.tf` `http_tokens=required` |
| Permission boundary en IAM | ❌ | Ausente → `AttachUserPolicy AdministratorAccess` surtió efecto | `iam.tf` `aws_iam_policy.permission_boundary` |
| MFA en usuarios con consola | ❌ | No requerido para `svc-monitoring` | P1 (remediation-plan) |
| Restricción de egreso (VPC) | ❌ | Sin firewall de egreso → 45.7 GB salieron | `vpc.tf` NACL + Flow Logs (visibilidad); P1 egress FW |
| Alerta de acceso masivo a S3 | ❌ | Sin correlación de volumen → exfil silenciosa | Sigma 03 + CloudTrail data events |
| SCP `DeleteTrail` deny | ✅ | Bloqueó el intento de anti-forense | SCP + `cloudtrail.tf` + Object Lock |
| GuardDuty | ⚠️ | **Detectó** el DNS exfil, pero la respuesta fue manual/tardía | `guardduty.tf` + respuesta automatizada (P1) |

## La cadena de tres entregables (el hilo de oro)

```
Sprint 2 (VAPT)      Sprint 3 (Terraform)          Sprint 4 (IR)
─────────────        ─────────────────────         ─────────────
V-03 SSRF     ──►    IMDSv2 required corta   ◄──    este breach ocurre si NO se mitiga
(encuentra)          la cadena de robo             (muestra el impacto + respuesta)
                     de credenciales
```

- **Sprint 2** encontró el SSRF (V-03) y lo documentó con PoC.
- **Sprint 3** lo mitiga a nivel infraestructura (IMDSv2 en el launch template) + añade las otras
  capas (permission boundary, GuardDuty, Object Lock) que habrían roto cada eslabón del breach.
- **Sprint 4** (este incidente) demuestra qué pasa **si esas capas no están** y cómo responder.

Cada "why" del RCA mapea a un control que el módulo del Sprint 3 provee. No son entregables
aislados: **VAPT encuentra → Terraform previene → IR responde.**

## Acciones (→ [remediation-plan.md](remediation-plan.md))

Los P1 derivan directamente de las capas que fallaron: forzar IMDSv2 (mata V-03), permission
boundaries, MFA, alerta de acceso masivo S3, y respuesta automatizada a GuardDuty. El control
sistémico es un **proceso de revisión periódica del baseline** — sin él, las capas vuelven a
degradarse.
