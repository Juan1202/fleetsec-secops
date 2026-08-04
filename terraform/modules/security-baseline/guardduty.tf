# FleetSec · security-baseline · GuardDuty
# Detector con S3 data events y malware protection + threat intel set. Detecta el DNS exfil
# y el acceso anómalo al bucket de PII del breach del Sprint 4; el threat intel set alimenta
# el playbook de IR con IoCs (p. ej. la IP Tor del egreso).

resource "aws_guardduty_detector" "this" {
  enable                       = true
  finding_publishing_frequency = "FIFTEEN_MINUTES"
  tags                         = local.common_tags
}

resource "aws_guardduty_detector_feature" "s3_data_events" {
  detector_id = aws_guardduty_detector.this.id
  name        = "S3_DATA_EVENTS"
  status      = "ENABLED"
}

resource "aws_guardduty_detector_feature" "malware_protection" {
  detector_id = aws_guardduty_detector.this.id
  name        = "EBS_MALWARE_PROTECTION"
  status      = "ENABLED"
}

resource "aws_guardduty_detector_feature" "rds_login" {
  detector_id = aws_guardduty_detector.this.id
  name        = "RDS_LOGIN_EVENTS"
  status      = "ENABLED"
}

# ---- Threat intel set (IoCs para el IR del Sprint 4) ----
resource "aws_s3_object" "threat_intel" {
  bucket       = aws_s3_bucket.logs.id
  key          = "threat-intel/known-bad-ips.txt"
  content      = "198.51.100.13\n203.0.113.66\n" # placeholders RFC5737 (ej. IP Tor del breach)
  content_type = "text/plain"
  kms_key_id   = aws_kms_key.s3.arn
  tags         = local.common_tags
}

resource "aws_guardduty_threatintelset" "iocs" {
  detector_id = aws_guardduty_detector.this.id
  name        = "${local.name}-iocs"
  format      = "TXT"
  location    = "https://s3.amazonaws.com/${aws_s3_bucket.logs.id}/${aws_s3_object.threat_intel.key}"
  activate    = true
  tags        = local.common_tags
}
