# terraform/

> AWS Hardening con Terraform — Sprint 3, Entregable 03.

Estructura esperada al cierre:
- `modules/security-baseline/` — IAM · KMS · S3 · VPC · RDS · Secrets · CloudTrail · Config · GuardDuty · SecurityHub · WAF
- `COMPLIANCE.md` — tabla de ≥10 controles mapeados a CIS v1.4 + ISO 27001:2022 + Ley 1581
- `checkov.yml` — supresiones documentadas (formato canónico)
- `examples/` — uso del módulo

DoD: `terraform validate` + `checkov` + `tflint` + `trivy config` todos limpios.

Tracking: [FSEC-20](https://jandresmoya982.atlassian.net/browse/FSEC-20) → [FSEC-22](https://jandresmoya982.atlassian.net/browse/FSEC-22)
