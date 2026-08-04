# FleetSec · security-baseline · CloudTrail + detección
# Trail multi-region, con validación de integridad y cifrado CMK, entregando a CloudWatch
# Logs. 4 metric filters + alarmas cubren las señales del breach del Sprint 4:
# el `AttachUserPolicy` (iam_changes), el root login, cambios de SG y API no autorizadas.

# ---- SNS de alarmas (cifrado con CMK) ----
resource "aws_sns_topic" "security_alarms" {
  name              = "${local.name}-security-alarms"
  kms_master_key_id = aws_kms_key.sns.id
  tags              = local.common_tags
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alarm_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.security_alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# ---- CloudWatch Log Group del trail (cifrado + retención) ----
resource "aws_cloudwatch_log_group" "trail" {
  name              = "/aws/cloudtrail/${local.name}"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.cloudtrail.arn
  tags              = local.common_tags
}

# ---- Rol para que CloudTrail escriba en CloudWatch Logs ----
data "aws_iam_policy_document" "trail_cwl_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "trail_cwl" {
  name               = "${local.name}-trail-to-cwl"
  assume_role_policy = data.aws_iam_policy_document.trail_cwl_assume.json
  tags               = local.common_tags
}

data "aws_iam_policy_document" "trail_cwl" {
  statement {
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.trail.arn}:*"]
  }
}

resource "aws_iam_role_policy" "trail_cwl" {
  name   = "${local.name}-trail-to-cwl"
  role   = aws_iam_role.trail_cwl.id
  policy = data.aws_iam_policy_document.trail_cwl.json
}

# ---- El trail ----
resource "aws_cloudtrail" "main" {
  name                          = "${local.name}-org-trail"
  s3_bucket_name                = aws_s3_bucket.logs.id
  is_multi_region_trail         = true
  include_global_service_events = true
  enable_log_file_validation    = true
  kms_key_id                    = aws_kms_key.cloudtrail.arn
  cloud_watch_logs_group_arn    = "${aws_cloudwatch_log_group.trail.arn}:*"
  cloud_watch_logs_role_arn     = aws_iam_role.trail_cwl.arn
  enable_logging                = true
  tags                          = local.common_tags

  # Data events sobre el bucket de PII (prod-drivers) — traza el acceso a los datos.
  event_selector {
    read_write_type           = "All"
    include_management_events = true
    data_resource {
      type   = "AWS::S3::Object"
      values = ["${aws_s3_bucket.data.arn}/"]
    }
  }

  depends_on = [aws_s3_bucket_policy.logs]
}

# ---- Metric filters + alarmas (CIS 4.x) ----
locals {
  metric_filters = {
    root_login = {
      pattern = "{ $.userIdentity.type = \"Root\" && $.userIdentity.invokedBy NOT EXISTS && $.eventType != \"AwsServiceEvent\" }"
      desc    = "Uso de la cuenta root"
    }
    iam_changes = {
      pattern = "{ ($.eventName = AttachUserPolicy) || ($.eventName = AttachRolePolicy) || ($.eventName = PutUserPolicy) || ($.eventName = PutRolePolicy) || ($.eventName = CreatePolicyVersion) }"
      desc    = "Cambios de politica IAM (incluye el AttachUserPolicy del breach)"
    }
    sg_changes = {
      pattern = "{ ($.eventName = AuthorizeSecurityGroupIngress) || ($.eventName = RevokeSecurityGroupIngress) || ($.eventName = CreateSecurityGroup) || ($.eventName = DeleteSecurityGroup) }"
      desc    = "Cambios en Security Groups"
    }
    unauthorized_api = {
      pattern = "{ ($.errorCode = \"*UnauthorizedOperation\") || ($.errorCode = \"AccessDenied*\") }"
      desc    = "Llamadas API no autorizadas"
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "this" {
  for_each       = local.metric_filters
  name           = "${local.name}-${each.key}"
  pattern        = each.value.pattern
  log_group_name = aws_cloudwatch_log_group.trail.name

  metric_transformation {
    name          = "${local.name}-${each.key}"
    namespace     = "FleetSec/Security"
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each            = local.metric_filters
  alarm_name          = "${local.name}-${each.key}"
  alarm_description   = each.value.desc
  namespace           = "FleetSec/Security"
  metric_name         = "${local.name}-${each.key}"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.security_alarms.arn]
  ok_actions          = [aws_sns_topic.security_alarms.arn]
  tags                = local.common_tags
}
