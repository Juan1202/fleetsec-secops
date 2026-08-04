# Tabla de cumplimiento · módulo `security-baseline` (FSEC-21)

> Cada control **PASS** apunta a la evidencia exacta (`archivo.tf` → recurso), no a un "cumple"
> genérico. Marcos: **CIS AWS Foundations Benchmark v1.4** · **ISO/IEC 27001:2022** (Anexo A) ·
> **Ley 1581 de 2012** (artículo + literal) · **NIST SP 800-53 Rev 5**.
> Ruta base de la evidencia: `terraform/modules/security-baseline/`.

## Resumen

| Métrica | Valor |
|---|---|
| Controles mapeados | **18** (≥10 requerido) |
| Estado | 18 PASS · 0 FAIL |
| Validaciones | `terraform validate` ✅ · `checkov` 0 FAILED ✅ · `tflint` ✅ · `trivy config` 0 ✅ |
| Fecha de verificación | 2026-08-03 |

## Matriz de trazabilidad

| # | Control | CIS AWS v1.4 | ISO 27001:2022 | Ley 1581 | NIST 800-53 | Status | Evidencia (path → recurso) |
|---|---|---|---|---|---|---|---|
| 1 | Password policy ≥14, 90d, reuse 24 | 1.8–1.11 | A.5.17 | — | IA-5 | ✅ PASS | `iam.tf` → `aws_iam_account_password_policy.strict` |
| 2 | **Permission boundary anti-escalada** (neutraliza el `AttachUserPolicy AdministratorAccess` del breach) | 1.16 | A.8.2, A.5.15 | Art. 4 lit. g (acceso restringido) | AC-6 | ✅ PASS | `iam.tf` → `aws_iam_policy.permission_boundary` + `permissions_boundary` en roles |
| 3 | Roles de servicio sin wildcards (least privilege) | 1.16 | A.8.2 | Art. 4 lit. g | AC-6 | ✅ PASS | `iam.tf` → `aws_iam_policy_document.app_task` (ARNs específicos) |
| 4 | Cifrado at-rest con CMK (S3, RDS, EBS, Secrets) | 2.1.1, 3.5–3.7 | A.8.24 | Art. 4 lit. g (seguridad) | SC-28 | ✅ PASS | `kms.tf` → `aws_kms_key.*` + `s3.tf`/`rds.tf` SSE-KMS |
| 5 | Rotación anual de CMK | 3.7 | A.8.24 | Art. 4 lit. g | SC-12 | ✅ PASS | `kms.tf` → `enable_key_rotation = true` (todas las CMK) |
| 6 | S3 Block Public Access (cuenta + bucket) | 2.1.5 | A.8.10, A.5.34 | Art. 4 lit. g | AC-3 | ✅ PASS | `s3.tf` → `aws_s3_account_public_access_block.this` + `aws_s3_bucket_public_access_block.*` |
| 7 | Cifrado in-transit forzado (deny sin TLS) | — | A.8.24 | Art. 4 lit. g | SC-8 | ✅ PASS | `s3.tf` → `data.aws_iam_policy_document.data_bucket` (DenyInsecureTransport) + `rds.force_ssl=1` |
| 8 | Inmutabilidad de logs (Object Lock COMPLIANCE) | — | A.8.15, A.5.28 | Art. 17 lit. d (conservación) | AU-9 | ✅ PASS | `s3.tf` → `aws_s3_bucket_object_lock_configuration.logs` (mode COMPLIANCE) |
| 9 | CloudTrail multi-region + log file validation + KMS | 3.1, 3.2 | A.8.15 | Art. 4 lit. g | AU-2, AU-6 | ✅ PASS | `cloudtrail.tf` → `aws_cloudtrail.main` (`is_multi_region_trail`, `enable_log_file_validation`) |
| 10 | Metric filters + alarmas (root, IAM, SG, API no autz.) | 4.1–4.14 | A.8.16 (monitoreo) | Art. 4 lit. g | SI-4, AU-6 | ✅ PASS | `cloudtrail.tf` → `aws_cloudwatch_log_metric_filter.this` + `aws_cloudwatch_metric_alarm.this` |
| 11 | CloudTrail data events sobre el bucket PII (`prod-drivers`) | 3.11 | A.8.15, A.8.12 (DLP) | Art. 4 lit. g | AU-2 | ✅ PASS | `cloudtrail.tf` → `aws_cloudtrail.main` `event_selector.data_resource` |
| 12 | GuardDuty (S3 data + malware + RDS login) | 4.16 | A.8.16 | Art. 4 lit. g | SI-4 | ✅ PASS | `guardduty.tf` → `aws_guardduty_detector.this` + `aws_guardduty_detector_feature.*` |
| 13 | **Threat intelligence** (IoCs → IR Sprint 4) | — | **A.5.7** (nuevo 2022) | — | RA-3 | ✅ PASS | `guardduty.tf` → `aws_guardduty_threatintelset.iocs` |
| 14 | Configuración continua evaluada (Config + 6 reglas) | 3.5 | **A.8.9** (nuevo 2022) | — | CM-6 | ✅ PASS | `config.tf` → `aws_config_configuration_recorder.this` + `aws_config_config_rule.managed` |
| 15 | VPC Flow Logs (visibilidad del egreso anómalo) | 3.9 | A.8.16, A.8.20 | — | AU-2 | ✅ PASS | `vpc.tf` → `aws_flow_log.s3` |
| 16 | Sin ingress 0.0.0.0/0; default SG vacío; RDS solo desde app | 5.2–5.4 | A.8.20 | — | SC-7 | ✅ PASS | `vpc.tf` → `aws_default_security_group.default` + `rds.tf` → `aws_vpc_security_group_ingress_rule.rds_from_app` |
| 17 | RDS Multi-AZ + backups (continuidad) | — | A.5.30 (nuevo 2022), A.8.13 | Art. 17 lit. d (conservación) | CP-10 | ✅ PASS | `rds.tf` → `aws_db_instance.main` (`multi_az=true`, `backup_retention_period=7`) |
| 18 | **IMDSv2 obligatorio** (mitiga V-03 SSRF a nivel infra) | — | A.8.20, A.8.9 | Art. 4 lit. g | SC-7 | ✅ PASS | `launch_template.tf` → `metadata_options { http_tokens = "required" }` |

## Nota sobre las citas Ley 1581 (punto de decisión #2 — a revisar por el autor)

Las medidas técnicas se anclan a dos deberes concretos, no a "cumple Ley 1581" genérico:

- **Art. 4, lit. g — Principio de seguridad:** *"…manejar con las medidas técnicas, humanas y administrativas necesarias para otorgar seguridad a los registros evitando su adulteración, pérdida, consulta, uso o acceso no autorizado o fraudulento."* → cifrado CMK (`kms.tf`, `s3.tf`, `rds.tf`), control de acceso (`iam.tf`), logging (`cloudtrail.tf`), detección (`guardduty.tf`).
- **Art. 17, lit. d — Deber de conservación del Responsable:** *"Conservar la información bajo las condiciones de seguridad necesarias para impedir su adulteración, pérdida, consulta, uso o acceso no autorizado o fraudulento."* → inmutabilidad de logs (Object Lock, `s3.tf`), continuidad (RDS Multi-AZ + backups, `rds.tf`).

> El **RNBD** (Registro Nacional de Bases de Datos, SIC) y las medidas **administrativas** (políticas, cláusulas con encargados, capacitación) son responsabilidad del equipo de privacidad — el módulo cubre la capa técnica.

## Cómo reproducir la evidencia

```bash
cd terraform/modules/security-baseline
terraform init -backend=false && terraform validate      # config válida
tflint                                                    # 0 issues
checkov -d . --config-file ../../checkov.yml              # 0 FAILED (supresiones canónicas)
trivy config . --ignorefile ../../../.trivyignore         # 0 misconfigurations
```
