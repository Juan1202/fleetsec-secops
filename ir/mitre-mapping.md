# Matriz MITRE ATT&CK · IR-2026-001

> 7 técnicas ATT&CK v14 con **manifestación específica** del breach + **contramedida D3FEND** +
> el control que la mitiga (módulo `security-baseline`, Sprint 3) y la regla que la detecta.

| # | Técnica | Táctica | Manifestación en el breach | Contramedida (D3FEND) | Mitigado por (Sprint 3) | Detección |
|---|---|---|---|---|---|---|
| 1 | **T1552.005** Unsecured Credentials: Cloud Instance Metadata API | Credential Access | **Vector de entrada:** el SSRF **V-03** (Sprint 2) alcanza el IMDS `169.254.169.254` y roba las credenciales del rol de la EC2 → esas son las de `svc-monitoring`. | Application Configuration Hardening (forzar **IMDSv2**) | `launch_template.tf` → `http_tokens = "required"` | GuardDuty (acceso IMDS anómalo) |
| 2 | **T1078.004** Valid Accounts: Cloud Accounts | Initial Access / Persistence | ConsoleLogin desde Tor `185.220.101.22` con creds válidas de `svc-monitoring` (T+00:00). | Multi-factor Authentication; Credential Rotation | IAM password policy + (MFA recomendado, P1) | **Sigma 04** (login desde Tor) |
| 3 | **T1098.001** Account Manipulation: Additional Cloud Credentials | Privilege Escalation / Persistence | `AttachUserPolicy AdministratorAccess` sobre `svc-monitoring` (T+00:15). | Resource Access Pattern Analysis; **least privilege / permission boundary** | `iam.tf` → `aws_iam_policy.permission_boundary` (neutraliza el attach) | **Sigma 01** (admin attach off-hours) |
| 4 | **T1530** Data from Cloud Storage Object | Collection | 387 `s3:GetObject` sobre `prod-drivers` (PII ~60k) en 30 min (T+00:30). | Restrict S3 Bucket Access (VPC endpoint condition) | `s3.tf` → BPA + TLS-deny + CloudTrail data events | **Sigma 03** (bulk S3 GetObject) |
| 5 | **T1567.002** Exfiltration to Cloud Storage | Exfiltration | Egreso de 45.7 GB a `185.220.101.22` (T+01:00). | Outbound Traffic Filtering; egress allowlist | `vpc.tf` → Flow Logs + NACL data deny-by-default | **Sigma 03** (correlación) + Flow Logs |
| 6 | **T1562.008** Impair Defenses: Disable Cloud Logs | Defense Evasion | `DeleteTrail` intentado (T+01:30) — **bloqueado por SCP**. | Configuration Hardening (SCP deny) + log immutability | `cloudtrail.tf` (multi-region) + Object Lock en `s3.tf` + SCP | **Sigma 02** (DeleteTrail/StopLogging) |
| 7 | **T1071.004** Application Layer Protocol: DNS | Command and Control | GuardDuty: DNS data exfiltration sobre `i-0abc1234def56789` (T+02:00). | DNS Traffic Analysis (Route53 Resolver DNS Firewall) | `guardduty.tf` (detector + threat intel set) | GuardDuty `Trojan:EC2/DNSDataExfiltration` |

## Cobertura y gaps (transparencia)

| Táctica | Cubierta | Gap declarado |
|---|---|---|
| Credential Access | T1552.005 (IMDSv2) | Robo de secretos de Secrets Manager no cubierto por regla dedicada |
| Initial Access | T1078.004 (Sigma 04) | Otros anonimizadores (VPN comerciales) requieren mantener la IP set por threat intel |
| Privilege Escalation | T1098.001 (Sigma 01) | `CreateAccessKey`/`CreateUser` no tienen regla propia (van en la P2) |
| Collection | T1530 (Sigma 03) | Lectura masiva de RDS no cubierta |
| Exfiltration | T1567.002 (Sigma 03 + Flow Logs) | DNS exfil se detecta por GuardDuty, no por Sigma core |
| Defense Evasion | T1562.008 (Sigma 02) | `DisableKeyRotation` no cubierto (candidato a nueva regla) |

> **El hilo de oro (VAPT → Terraform → IR):** la técnica #1 (T1552.005 / IMDS) es donde el **V-03
> SSRF** del Sprint 2 se convierte en el punto de entrada del breach, y es exactamente lo que el
> **IMDSv2** del módulo del Sprint 3 corta. Ver [rca.md](rca.md).

## Referencias

- MITRE ATT&CK v14 · https://attack.mitre.org/
- MITRE D3FEND · https://d3fend.mitre.org/
- Los IDs D3FEND se citan por nombre de contramedida; ver el grafo de D3FEND para el mapeo ATT&CK↔D3FEND por técnica.
