# FleetSec · security-baseline · outputs (para módulos downstream)

output "vpc_id" {
  description = "ID de la VPC."
  value       = aws_vpc.main.id
}

output "app_subnet_ids" {
  description = "IDs de las subredes del tier app."
  value       = [for s in aws_subnet.app : s.id]
}

output "data_subnet_ids" {
  description = "IDs de las subredes del tier data."
  value       = [for s in aws_subnet.data : s.id]
}

output "app_security_group_id" {
  description = "SG del tier app."
  value       = aws_security_group.app.id
}

output "permission_boundary_arn" {
  description = "ARN del permission boundary anti-escalada (adjuntar a todo rol/usuario)."
  value       = aws_iam_policy.permission_boundary.arn
}

output "logs_bucket_arn" {
  description = "ARN del bucket de logs de auditoría (inmutable)."
  value       = aws_s3_bucket.logs.arn
}

output "data_bucket_arn" {
  description = "ARN del bucket de datos (prod-drivers, PII)."
  value       = aws_s3_bucket.data.arn
}

output "kms_key_arns" {
  description = "ARNs de las CMK por servicio."
  value = {
    s3         = aws_kms_key.s3.arn
    rds        = aws_kms_key.rds.arn
    secrets    = aws_kms_key.secrets.arn
    ebs        = aws_kms_key.ebs.arn
    sns        = aws_kms_key.sns.arn
    cloudtrail = aws_kms_key.cloudtrail.arn
  }
}

output "security_alarms_topic_arn" {
  description = "ARN del topic SNS de alarmas de seguridad."
  value       = aws_sns_topic.security_alarms.arn
}

output "waf_web_acl_arn" {
  description = "ARN del WAF v2 (asociar al ALB)."
  value       = aws_wafv2_web_acl.alb.arn
}

output "rds_endpoint" {
  description = "Endpoint de la instancia RDS."
  value       = aws_db_instance.main.address
}

output "app_launch_template_id" {
  description = "ID del launch template con IMDSv2 obligatorio."
  value       = aws_launch_template.app.id
}
