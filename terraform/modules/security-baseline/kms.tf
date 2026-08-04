# FleetSec · security-baseline · KMS
# Una CMK por servicio, rotación anual habilitada (CIS 3.7). Políticas sin Principal "*":
# root admin + el principal de servicio exacto que consume la clave.

# ---- S3 ----
resource "aws_kms_key" "s3" {
  description             = "${local.name}-s3-cmk"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_generic.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "s3" {
  name          = "alias/${local.name}-s3"
  target_key_id = aws_kms_key.s3.key_id
}

# ---- RDS ----
resource "aws_kms_key" "rds" {
  description             = "${local.name}-rds-cmk"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_generic.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "rds" {
  name          = "alias/${local.name}-rds"
  target_key_id = aws_kms_key.rds.key_id
}

# ---- Secrets Manager ----
resource "aws_kms_key" "secrets" {
  description             = "${local.name}-secrets-cmk"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_generic.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${local.name}-secrets"
  target_key_id = aws_kms_key.secrets.key_id
}

# ---- EBS (para launch templates / IMDSv2) ----
resource "aws_kms_key" "ebs" {
  description             = "${local.name}-ebs-cmk"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_generic.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "ebs" {
  name          = "alias/${local.name}-ebs"
  target_key_id = aws_kms_key.ebs.key_id
}

# ---- SNS (alarmas de seguridad) ----
resource "aws_kms_key" "sns" {
  description             = "${local.name}-sns-cmk"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_sns.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "sns" {
  name          = "alias/${local.name}-sns"
  target_key_id = aws_kms_key.sns.key_id
}

# ---- CloudTrail + CloudWatch Logs ----
resource "aws_kms_key" "cloudtrail" {
  description             = "${local.name}-cloudtrail-cmk"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_cloudtrail.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "cloudtrail" {
  name          = "alias/${local.name}-cloudtrail"
  target_key_id = aws_kms_key.cloudtrail.key_id
}

# ---- Política genérica: root admin + uso vía servicios AWS de la cuenta ----
data "aws_iam_policy_document" "kms_generic" {
  statement {
    sid    = "RootAccountAdmin"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:${local.partition}:iam::${local.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowServiceUseViaAccount"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:${local.partition}:iam::${local.account_id}:root"]
    }
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:ReEncrypt*",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:CallerAccount"
      values   = [local.account_id]
    }
  }
}

# ---- Política SNS: root admin + servicio SNS/CloudWatch ----
data "aws_iam_policy_document" "kms_sns" {
  statement {
    sid    = "RootAccountAdmin"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:${local.partition}:iam::${local.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowCloudWatchAndSNS"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com", "sns.amazonaws.com"]
    }
    actions   = ["kms:Decrypt", "kms:GenerateDataKey*"]
    resources = ["*"]
  }
}

# ---- Política CloudTrail: root admin + servicio CloudTrail + CloudWatch Logs ----
data "aws_iam_policy_document" "kms_cloudtrail" {
  statement {
    sid    = "RootAccountAdmin"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:${local.partition}:iam::${local.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowCloudTrailEncrypt"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    actions   = ["kms:GenerateDataKey*", "kms:DescribeKey"]
    resources = ["*"]
    condition {
      test     = "StringLike"
      variable = "kms:EncryptionContext:aws:cloudtrail:arn"
      values   = ["arn:${local.partition}:cloudtrail:*:${local.account_id}:trail/*"]
    }
  }

  statement {
    sid    = "AllowCloudWatchLogs"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["logs.${local.region}.amazonaws.com"]
    }
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:ReEncrypt*",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = ["*"]
    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:${local.partition}:logs:${local.region}:${local.account_id}:log-group:*"]
    }
  }
}
