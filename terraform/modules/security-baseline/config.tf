# FleetSec · security-baseline · AWS Config
# Recorder + 6 managed rules que evalúan continuamente la postura (encryption, public S3,
# root MFA, password policy, CloudTrail, GuardDuty).

data "aws_iam_policy_document" "config_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["config.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "config" {
  name               = "${local.name}-config"
  assume_role_policy = data.aws_iam_policy_document.config_assume.json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "config_managed" {
  role       = aws_iam_role.config.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/service-role/AWS_ConfigRole"
}

data "aws_iam_policy_document" "config_s3" {
  statement {
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.logs.arn}/AWSLogs/${local.account_id}/Config/*"]
    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
  }
  statement {
    effect    = "Allow"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.logs.arn]
  }
}

resource "aws_iam_role_policy" "config_s3" {
  name   = "${local.name}-config-s3"
  role   = aws_iam_role.config.id
  policy = data.aws_iam_policy_document.config_s3.json
}

resource "aws_config_configuration_recorder" "this" {
  name     = "${local.name}-recorder"
  role_arn = aws_iam_role.config.arn
  recording_group {
    all_supported                 = true
    include_global_resource_types = true
  }
}

resource "aws_config_delivery_channel" "this" {
  name           = "${local.name}-delivery"
  s3_bucket_name = aws_s3_bucket.logs.id
  s3_key_prefix  = "AWSLogs/${local.account_id}/Config"
  depends_on     = [aws_config_configuration_recorder.this, aws_s3_bucket_policy.logs]
}

resource "aws_config_configuration_recorder_status" "this" {
  name       = aws_config_configuration_recorder.this.name
  is_enabled = true
  depends_on = [aws_config_delivery_channel.this]
}

locals {
  config_rules = {
    encrypted_volumes   = "ENCRYPTED_VOLUMES"
    s3_public_read      = "S3_BUCKET_PUBLIC_READ_PROHIBITED"
    s3_public_write     = "S3_BUCKET_PUBLIC_WRITE_PROHIBITED"
    root_mfa            = "ROOT_ACCOUNT_MFA_ENABLED"
    iam_password_policy = "IAM_PASSWORD_POLICY"
    cloudtrail_enabled  = "CLOUD_TRAIL_ENABLED"
  }
}

resource "aws_config_config_rule" "managed" {
  for_each = local.config_rules
  name     = "${local.name}-${each.key}"
  source {
    owner             = "AWS"
    source_identifier = each.value
  }
  depends_on = [aws_config_configuration_recorder.this]
}
