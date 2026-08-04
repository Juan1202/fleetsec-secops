# FleetSec · security-baseline · RDS PostgreSQL
# Multi-AZ (Ley 1581 Art. 17 continuidad), cifrado CMK (Art. 4 seguridad), sin endpoint
# público, TLS forzado, master password gestionado por AWS (nunca hardcodeado).

resource "aws_security_group" "app" {
  name        = "${local.name}-app-sg"
  description = "SG del tier app (compute). Egreso restringido; sin ingress publico."
  vpc_id      = aws_vpc.main.id
  tags        = merge(local.common_tags, { Name = "${local.name}-app-sg" })
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-rds-sg"
  description = "SG de RDS: solo 5432 desde el tier app."
  vpc_id      = aws_vpc.main.id
  tags        = merge(local.common_tags, { Name = "${local.name}-rds-sg" })
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL desde el tier app"
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_egress_rule" "app_to_rds" {
  security_group_id            = aws_security_group.app.id
  description                  = "Salida a RDS PostgreSQL"
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.rds.id
}

resource "aws_vpc_security_group_egress_rule" "app_https" {
  security_group_id = aws_security_group.app.id
  description       = "Salida HTTPS (APIs/updates via NAT)"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db-subnets"
  subnet_ids = [for s in aws_subnet.data : s.id]
  tags       = local.common_tags
}

resource "aws_db_parameter_group" "app" {
  name   = "${local.name}-pg16"
  family = "postgres16"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  parameter {
    name  = "log_connections"
    value = "1"
  }
  parameter {
    name  = "log_disconnections"
    value = "1"
  }
  parameter {
    name  = "log_statement"
    value = "ddl"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }
  tags = local.common_tags
}

# Rol para Enhanced Monitoring (CKV_AWS_118).
data "aws_iam_policy_document" "rds_monitoring_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["monitoring.rds.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "rds_monitoring" {
  name               = "${local.name}-rds-monitoring"
  assume_role_policy = data.aws_iam_policy_document.rds_monitoring_assume.json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name}-app-db"
  engine         = "postgres"
  engine_version = var.rds_engine_version
  instance_class = "db.t3.medium"

  allocated_storage     = 50
  max_allocated_storage = 200
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.rds.arn

  db_name  = "fleetsec"
  username = "dbadmin"
  # Master password gestionado por AWS en Secrets Manager (nunca hardcodeado).
  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.rds.arn

  multi_az               = true
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.app.name

  iam_database_authentication_enabled = true
  backup_retention_period             = 7
  backup_window                       = "03:00-04:00"
  copy_tags_to_snapshot               = true
  deletion_protection                 = true
  auto_minor_version_upgrade          = true
  skip_final_snapshot                 = false
  final_snapshot_identifier           = "${local.name}-app-db-final"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  monitoring_interval             = 60
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn
  performance_insights_enabled    = true
  performance_insights_kms_key_id = aws_kms_key.rds.arn

  tags = merge(local.common_tags, { DataClass = "PII", Ley1581 = "true" })
}
