# terraform/

> AWS Hardening con Terraform — Sprint 3, Entregable 03.

## Contenido

- [`modules/security-baseline/`](modules/security-baseline/) — el módulo reutilizable:
  IAM (permission boundaries) · KMS (CMK por servicio) · S3 (BPA + Object Lock) · VPC 3-tier +
  Flow Logs · RDS Multi-AZ · Secrets · CloudTrail + Config + GuardDuty + Security Hub · WAF v2 ·
  launch template con IMDSv2. Ver su [README](modules/security-baseline/README.md) (incluye el
  diagrama, la sección "cómo mitiga el breach del Sprint 4" y las supresiones).
- [`COMPLIANCE.md`](COMPLIANCE.md) — tabla de **18 controles** mapeados a CIS AWS v1.4 +
  ISO 27001:2022 + Ley 1581 + NIST 800-53, con evidencia por path.
- [`checkov.yml`](checkov.yml) — supresiones de Checkov en formato canónico (5 campos).

## Validación (plan-only, sin apply)

```bash
cd modules/security-baseline
terraform init -backend=false && terraform validate   # Success
tflint                                                 # 0 issues
checkov -d . --config-file ../../checkov.yml           # 0 FAILED
trivy config . --ignorefile ../../../.trivyignore      # 0 misconfigurations
```

Estado al cierre: `terraform validate` + `checkov` + `tflint` + `trivy config` **todos limpios**.

Decisión arquitectónica: [ADR-011 — una CMK por servicio](../docs/ADRs/ADR-011-cmk-per-service.md).

Tracking: [FSEC-20](https://jandresmoya982.atlassian.net/browse/FSEC-20) → [FSEC-22](https://jandresmoya982.atlassian.net/browse/FSEC-22)
