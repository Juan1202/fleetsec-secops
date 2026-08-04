# IOCs · IR-2026-001 · Compromiso de `svc-monitoring` y exfiltración de `prod-drivers`

> Indicadores de compromiso del incidente, enriquecidos con ≥2 fuentes cada uno.
> **Fecha de detección:** 2026-07-28 · **Severidad:** SEV-1 · **Estado:** Contenido.
> Cuenta AWS `123456789012` · región `us-east-1`.

## Resumen del incidente

Un actor accedió a la consola AWS con credenciales válidas de `svc-monitoring` desde un nodo de
salida Tor, escaló privilegios vía `AttachUserPolicy AdministratorAccess`, exfiltró ~45.7 GB del
bucket de PII `prod-drivers` (~60.000 conductores), intentó borrar CloudTrail (bloqueado por SCP) y
generó tráfico DNS de exfiltración desde una EC2. **Hipótesis de causa raíz (ver [RCA](rca.md)):
el vector de entrada fue el SSRF V-03 del VAPT (Sprint 2), que alcanza el IMDS y roba las
credenciales del rol de la EC2.**

## Tabla de IOCs

| Tipo | Valor | Primera obs. (UTC) | Enrichment (≥2 fuentes) | Confianza |
|---|---|---|---|---|
| IPv4 (C2/exfil) | `185.220.101.22` | 2026-07-28T02:00:00Z | **Tor exit node confirmado** (Tor Project *bulk exit list* + `dan.me.uk/tornodes`); AbuseIPDB: reportada, categorías 14/18/19 (port scan/hacking/bruteforce); OTX AlienVault: presente en pulsos de "Tor exit / anonymized abuse"; Shodan: sin servicios legítimos expuestos, rango de relays Tor. | **Alta** |
| IAM user | `svc-monitoring` | 2026-07-28T02:00:00Z | CloudTrail: `ConsoleLogin` + `AttachUserPolicy` desde `185.220.101.22`; uso previo solo desde IPs internas (baseline) → desviación clara. | **Alta** |
| Instancia EC2 | `i-0abc1234def56789` | 2026-07-28T04:00:00Z | GuardDuty finding `Trojan:EC2/DNSDataExfiltration`; VPC Flow Logs: egreso anómalo; rol de instancia = origen probable de las creds robadas vía IMDS (V-03). | **Alta** |
| Bucket S3 | `prod-drivers` | 2026-07-28T02:30:00Z | CloudTrail data events: 387 `GetObject` en 30 min desde `svc-monitoring`; contiene PII (cédula, email, teléfono, geolocalización) de ~60k titulares. | **Alta** |
| Acción IAM | `AttachUserPolicy` → `arn:aws:iam::aws:policy/AdministratorAccess` | 2026-07-28T02:15:00Z | CloudTrail `eventName=AttachUserPolicy`; off-hours; principal `svc-monitoring` sobre sí mismo. | **Alta** |
| Volumen exfil | `45.7 GB` → `185.220.101.22` | 2026-07-28T03:00:00Z | VPC Flow Logs: `bytes` agregados a destino no corporativo; correlaciona con la ventana de `GetObject`. | **Media-Alta** |

## Timeline (IOC → evento)

| Tiempo | UTC | Evento | IOC |
|---|---|---|---|
| T+00:00 | 2026-07-28T02:00:00Z | `ConsoleLogin` desde Tor con creds válidas | `185.220.101.22`, `svc-monitoring` |
| T+00:15 | 2026-07-28T02:15:00Z | `AttachUserPolicy AdministratorAccess` | `svc-monitoring` |
| T+00:30 | 2026-07-28T02:30:00Z | 387 `s3:GetObject` sobre `prod-drivers` | `prod-drivers` |
| T+01:00 | 2026-07-28T03:00:00Z | Egreso 45.7 GB | `185.220.101.22` |
| T+01:30 | 2026-07-28T03:30:00Z | `DeleteTrail` intentado — **bloqueado por SCP** | CloudTrail |
| T+02:00 | 2026-07-28T04:00:00Z | GuardDuty: DNS exfil | `i-0abc1234def56789` |

## Carga del threat intel set a GuardDuty

Los IOCs de red se cargan al detector GuardDuty del baseline (Sprint 3) para detección continua.
**La IP Tor real (`185.220.101.22`) reemplaza el placeholder** del módulo Terraform:

```bash
# Subir la lista de IoCs al bucket de evidencia (Object Lock)
printf '185.220.101.22\n' > /tmp/2026-001.txt
aws s3 cp /tmp/2026-001.txt s3://ir-evidence-locked/ti-sets/2026-001.txt

# Registrar el threat intel set en el detector
DETECTOR_ID=$(aws guardduty list-detectors --query 'DetectorIds[0]' --output text)
aws guardduty create-threat-intel-set \
  --detector-id "$DETECTOR_ID" \
  --name fsec-ir-2026-001 \
  --format TXT \
  --location s3://ir-evidence-locked/ti-sets/2026-001.txt \
  --activate
```
> El módulo `security-baseline` (Sprint 3, `guardduty.tf` → `aws_guardduty_threatintelset.iocs`)
> ya provee la infraestructura; aquí se sustituye el placeholder por el IoC real del incidente.

## Notas de fiabilidad

- El estatus **Tor exit** de `185.220.101.22` es verificable de forma independiente y estable en el
  tiempo (rango `185.220.101.0/24`, relays Tor conocidos) — es el IOC de mayor confianza.
- Los puntajes de reputación (AbuseIPDB/OTX) reflejan el estado al momento de la investigación
  (2026-07-28) y deben re-verificarse antes de acciones legales.
- La atribución del volumen exfil (45.7 GB) a `185.220.101.22` es correlación por ventana temporal
  en Flow Logs, no captura de payload (cifrado en tránsito).
