# FleetSec · security-baseline · Security Hub
# Agregación de hallazgos con los estándares FSBP + CIS AWS Foundations v1.4.0.

resource "aws_securityhub_account" "this" {
  enable_default_standards = false
}

resource "aws_securityhub_standards_subscription" "fsbp" {
  standards_arn = "arn:${local.partition}:securityhub:${local.region}::standards/aws-foundational-security-best-practices/v/1.0.0"
  depends_on    = [aws_securityhub_account.this]
}

resource "aws_securityhub_standards_subscription" "cis" {
  standards_arn = "arn:${local.partition}:securityhub:${local.region}::standards/cis-aws-foundations-benchmark/v/1.4.0"
  depends_on    = [aws_securityhub_account.this]
}
