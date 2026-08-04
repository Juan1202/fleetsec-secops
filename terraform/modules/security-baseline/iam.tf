# FleetSec · security-baseline · IAM
# Controles clave anti-breach (Sprint 4):
#  - Permission boundary: capa el MÁXIMO de permisos efectivos. Aunque un principal
#    comprometido intente `AttachUserPolicy AdministratorAccess`, el boundary lo neutraliza
#    (admin queda fuera del techo). Además niega explícitamente la escalada y el tampering
#    de controles de seguridad (DeleteTrail/StopLogging, disable GuardDuty).
#  - Password policy fuerte (CIS 1.8-1.11).
#  - Roles por servicio sin wildcards de acción (least privilege).

# ---- Password policy (CIS 1.8-1.11) ----
resource "aws_iam_account_password_policy" "strict" {
  minimum_password_length        = 14
  require_lowercase_characters   = true
  require_uppercase_characters   = true
  require_numbers                = true
  require_symbols                = true
  allow_users_to_change_password = true
  max_password_age               = 90
  password_reuse_prevention      = 24
  hard_expiry                    = false
}

# ---- Permission boundary (el techo de permisos anti-escalada) ----
data "aws_iam_policy_document" "permission_boundary" {
  # Allowlist: operación y monitoreo. NO incluye IAM-write ni admin → attach de
  # AdministratorAccess no surte efecto (queda fuera del techo).
  statement {
    sid    = "AllowOperationalReadAndMonitoring"
    effect = "Allow"
    actions = [
      "cloudwatch:GetMetricData",
      "cloudwatch:ListMetrics",
      "cloudwatch:PutMetricData",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
      "ec2:DescribeInstances",
      "ec2:DescribeTags",
      "s3:GetObject",
      "s3:PutObject",
      "s3:ListBucket",
      "kms:Decrypt",
      "kms:GenerateDataKey",
      "secretsmanager:GetSecretValue",
    ]
    resources = ["*"]
  }

  # Deny explícito de escalada de privilegios y tampering de seguridad (defensa en profundidad).
  statement {
    sid    = "DenyPrivilegeEscalationAndSecurityTampering"
    effect = "Deny"
    actions = [
      "iam:AttachUserPolicy",
      "iam:AttachRolePolicy",
      "iam:PutUserPolicy",
      "iam:PutRolePolicy",
      "iam:CreatePolicyVersion",
      "iam:SetDefaultPolicyVersion",
      "iam:CreateAccessKey",
      "iam:DeleteUserPermissionsBoundary",
      "iam:DeleteRolePermissionsBoundary",
      "iam:PutUserPermissionsBoundary",
      "iam:PutRolePermissionsBoundary",
      "cloudtrail:StopLogging",
      "cloudtrail:DeleteTrail",
      "cloudtrail:UpdateTrail",
      "guardduty:DeleteDetector",
      "guardduty:UpdateDetector",
      "config:DeleteConfigurationRecorder",
      "config:StopConfigurationRecorder",
      "kms:ScheduleKeyDeletion",
      "kms:DisableKeyRotation",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "permission_boundary" {
  name        = "${local.name}-permission-boundary"
  description = "Techo de permisos anti-escalada (neutraliza AttachUserPolicy AdministratorAccess del breach)."
  policy      = data.aws_iam_policy_document.permission_boundary.json
  tags        = local.common_tags
}

# ---- Rol de monitoreo (modela svc-monitoring del breach, ahora acotado) ----
data "aws_iam_policy_document" "assume_ec2" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "svc_monitoring" {
  name                 = "${local.name}-svc-monitoring"
  assume_role_policy   = data.aws_iam_policy_document.assume_ec2.json
  permissions_boundary = aws_iam_policy.permission_boundary.arn
  tags                 = local.common_tags
}

data "aws_iam_policy_document" "svc_monitoring" {
  statement {
    sid    = "MonitoringReadOnly"
    effect = "Allow"
    actions = [
      "cloudwatch:GetMetricData",
      "cloudwatch:ListMetrics",
      "cloudwatch:PutMetricData",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "svc_monitoring" {
  name   = "${local.name}-svc-monitoring-inline"
  role   = aws_iam_role.svc_monitoring.id
  policy = data.aws_iam_policy_document.svc_monitoring.json
}

resource "aws_iam_instance_profile" "svc_monitoring" {
  name = "${local.name}-svc-monitoring"
  role = aws_iam_role.svc_monitoring.name
  tags = local.common_tags
}

# ---- Rol de aplicación (ECS task) — least privilege sobre el bucket de datos + CMK + secret ----
data "aws_iam_policy_document" "assume_ecs" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app_task" {
  name                 = "${local.name}-app-task"
  assume_role_policy   = data.aws_iam_policy_document.assume_ecs.json
  permissions_boundary = aws_iam_policy.permission_boundary.arn
  tags                 = local.common_tags
}

data "aws_iam_policy_document" "app_task" {
  statement {
    sid       = "DataBucketObjectRW"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.data.arn}/*"]
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["true"]
    }
  }
  statement {
    sid       = "DataBucketList"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.data.arn]
  }
  statement {
    sid       = "UseDataKeys"
    effect    = "Allow"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = [aws_kms_key.s3.arn, aws_kms_key.rds.arn]
  }
  statement {
    sid       = "ReadDbSecret"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db.arn]
  }
}

resource "aws_iam_role_policy" "app_task" {
  name   = "${local.name}-app-task-inline"
  role   = aws_iam_role.app_task.id
  policy = data.aws_iam_policy_document.app_task.json
}
