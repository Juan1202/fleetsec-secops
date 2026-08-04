# FleetSec · security-baseline · input variables

variable "env" {
  description = "Nombre del ambiente (prefijo de recursos). Ej: prod, staging."
  type        = string
  default     = "prod"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.env))
    error_message = "env debe ser minúsculas/números/guiones, 2-21 chars."
  }
}

variable "aws_region" {
  description = "Región AWS primaria. us-east-1 por latencia/costo desde Colombia (AWS no tiene región local)."
  type        = string
  default     = "us-east-1"
}

variable "azs" {
  description = "Zonas de disponibilidad (>=2 para Multi-AZ)."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]

  validation {
    condition     = length(var.azs) >= 2
    error_message = "Se requieren al menos 2 AZs para alta disponibilidad (Ley 1581 Art. 17)."
  }
}

variable "vpc_cidr" {
  description = "CIDR de la VPC."
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0))
    error_message = "vpc_cidr debe ser un CIDR IPv4 válido."
  }
}

variable "allowed_country_codes" {
  description = "Códigos ISO de país permitidos por el WAF (negocio Colombia + expansión regional)."
  type        = list(string)
  default     = ["CO", "PE", "US"]
}

variable "waf_rate_limit" {
  description = "Límite de requests por IP en 5 min sobre /api/auth/* (anti fuerza-bruta, refuerza V-07)."
  type        = number
  default     = 1000
}

variable "log_retention_days" {
  description = "Retención de los CloudWatch Log Groups (días)."
  type        = number
  default     = 365
}

variable "object_lock_days" {
  description = "Días de inmutabilidad (Object Lock COMPLIANCE) del bucket de logs de auditoría."
  type        = number
  default     = 365
}

variable "rds_engine_version" {
  description = "Versión de PostgreSQL para RDS."
  type        = string
  default     = "16.4"
}

variable "alarm_email" {
  description = "Email para las alarmas de seguridad (SNS). Vacío = no se crea la suscripción."
  type        = string
  default     = ""
}

variable "tags" {
  description = "Tags comunes aplicados a todos los recursos."
  type        = map(string)
  default = {
    Project    = "fleetsec"
    ManagedBy  = "terraform"
    Compliance = "CIS-AWS-1.4;ISO-27001;Ley-1581"
  }
}
