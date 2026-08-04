# FleetSec · security-baseline · Secrets Manager
# Secreto de credenciales de la app cifrado con CMK dedicada. La rotación (30d) se cablea
# vía un Lambda provisto por el consumidor (hook), para no acoplar el baseline a una función.

variable "db_rotation_lambda_arn" {
  description = "ARN del Lambda de rotación del secreto (opcional). Vacío = rotación no activada en el baseline."
  type        = string
  default     = ""
}

resource "aws_secretsmanager_secret" "db" {
  name                    = "${local.name}/rds/app-credentials"
  description             = "Credenciales de aplicación para RDS (cifradas con CMK)."
  kms_key_id              = aws_kms_key.secrets.arn
  recovery_window_in_days = 30
  tags                    = local.common_tags
}

# Rotación 30d — activa solo si se provee el Lambda (el baseline expone el hook).
resource "aws_secretsmanager_secret_rotation" "db" {
  count               = var.db_rotation_lambda_arn != "" ? 1 : 0
  secret_id           = aws_secretsmanager_secret.db.id
  rotation_lambda_arn = var.db_rotation_lambda_arn
  rotation_rules {
    automatically_after_days = 30
  }
}
