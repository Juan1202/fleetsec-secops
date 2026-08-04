# FleetSec · security-baseline · provider requirements
# Diseñado para `terraform validate` + Checkov + tflint + trivy config SIN apply ni
# credenciales AWS reales (plan-only / static analysis). Ver README § "Uso y validación".

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.60.0, < 6.0.0"
    }
  }
}
