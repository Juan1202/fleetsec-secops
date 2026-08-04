# FleetSec · security-baseline · WAF v2 (REGIONAL, para ALB)
# SQLi + KnownBadInputs en BLOCK; CommonRuleSet en COUNT (fase de tuning antes de BLOCK);
# rate-limit sobre /api/auth/* (refuerza V-07); geo-allowlist CO/PE/US. Protege la superficie
# que en la app tenía el SSRF (V-03) y la fuerza-bruta (V-07).

resource "aws_cloudwatch_log_group" "waf" {
  name              = "aws-waf-logs-${local.name}"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.cloudtrail.arn
  tags              = local.common_tags
}

resource "aws_wafv2_web_acl" "alb" {
  name        = "${local.name}-alb-acl"
  description = "Baseline WAF para el ALB de FleetSec"
  scope       = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "AWSManagedRulesSQLiRuleSet"
    priority = 1
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.name}-sqli"
    }
  }

  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 2
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.name}-badinputs"
    }
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 3
    override_action {
      count {} # COUNT primero: tuning de falsos positivos antes de pasar a BLOCK
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.name}-common"
    }
  }

  rule {
    name     = "rate-limit-auth"
    priority = 10
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = var.waf_rate_limit
        aggregate_key_type = "IP"
        scope_down_statement {
          byte_match_statement {
            search_string         = "/api/auth/"
            positional_constraint = "STARTS_WITH"
            field_to_match {
              uri_path {}
            }
            text_transformation {
              priority = 0
              type     = "NONE"
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.name}-ratelimit"
    }
  }

  rule {
    name     = "geo-allowlist"
    priority = 20
    action {
      block {}
    }
    statement {
      not_statement {
        statement {
          geo_match_statement {
            country_codes = var.allowed_country_codes
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.name}-geo"
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    sampled_requests_enabled   = true
    metric_name                = "${local.name}-alb-acl"
  }

  tags = local.common_tags
}

resource "aws_wafv2_web_acl_logging_configuration" "alb" {
  resource_arn            = aws_wafv2_web_acl.alb.arn
  log_destination_configs = [aws_cloudwatch_log_group.waf.arn]

  redacted_fields {
    single_header {
      name = "authorization"
    }
  }
}
