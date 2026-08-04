# ADR-011 · Una CMK de KMS por servicio (vs. una CMK compartida)

- **Estado:** Aceptada
- **Fecha:** 2026-08-03
- **Contexto:** FSEC-20 (módulo Terraform `security-baseline`)

## Context

El módulo `security-baseline` cifra datos en reposo con claves gestionadas por el cliente (CMK)
en múltiples servicios: S3 (bucket de PII + logs), RDS, Secrets Manager, EBS, SNS y CloudTrail.
Hay que decidir si usar **una sola CMK compartida** para todo o **una CMK por servicio**. La
decisión afecta el radio de impacto ante compromiso de una clave, la granularidad de las
políticas de clave, el costo y la operación de rotación.

## Decision

Usar **una CMK dedicada por servicio** (`s3`, `rds`, `secrets`, `ebs`, `sns`, `cloudtrail`), cada
una con rotación anual habilitada y una **key policy específica** que concede uso solo al
principal de servicio que la consume (p. ej. la CMK de CloudTrail concede a `cloudtrail.amazonaws.com`
y a `logs.<region>.amazonaws.com` con condición de EncryptionContext; ninguna usa `Principal: "*"`).

## Consequences

### Positivas
- **Radio de impacto acotado:** comprometer o programar la eliminación de una clave afecta solo a
  ese servicio, no a todo el estado cifrado.
- **Least privilege real en la key policy:** cada política concede uso al principal exacto, con
  condiciones (EncryptionContext para CloudTrail/Logs) — sin `Principal: "*"`.
- **Separación de deberes / auditoría:** el uso de cada clave (CloudTrail data events) es atribuible
  a un servicio; facilita el forense del Sprint 4.
- **Alineación de cumplimiento:** CIS AWS 3.5-3.7 y Ley 1581 Art. 4 (seguridad) se evidencian por
  servicio.

### Negativas
- **Más recursos que gestionar** (6 CMK + alias vs. 1) → más líneas de Terraform y más claves que
  rotar/monitorear.
- **Costo:** cada CMK tiene un costo mensual fijo (~USD 1/mes) + costo por request. Marginal frente
  al valor del control, pero no nulo.

### Neutras
- La rotación anual está habilitada en todas por igual; no cambia el modelo operativo entre una y
  varias claves.
- La política genérica (`kms_generic`) se reutiliza para S3/RDS/Secrets/EBS; solo SNS y CloudTrail
  requieren políticas propias por sus principals de servicio.

## Alternativa descartada

**Una CMK compartida:** menos recursos y menor costo, pero un único punto de compromiso para todo
el dato cifrado y una key policy que necesariamente amplía los principals con acceso — contrario al
principio de menor privilegio y peor postura ante el escenario de brecha del Sprint 4.
