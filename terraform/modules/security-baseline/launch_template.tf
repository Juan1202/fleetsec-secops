# FleetSec · security-baseline · EC2 launch template
# IMDSv2 OBLIGATORIO (http_tokens=required) → mitiga el SSRF (V-03) a nivel infraestructura:
# aunque una app alcance 169.254.169.254, sin el token de sesión IMDSv2 no obtiene credenciales.
# Defense-in-depth: la app se remedia en Sprint 2 (SsrfGuard); la infra lo refuerza aquí.

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_launch_template" "app" {
  name          = "${local.name}-app-lt"
  image_id      = data.aws_ssm_parameter.al2023.value
  instance_type = "t3.small"

  iam_instance_profile {
    name = aws_iam_instance_profile.svc_monitoring.name
  }

  vpc_security_group_ids = [aws_security_group.app.id]

  # IMDSv2 obligatorio — el control que mitiga V-03.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "enabled"
  }

  monitoring {
    enabled = true
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      encrypted             = true
      kms_key_id            = aws_kms_key.ebs.arn
      volume_size           = 20
      volume_type           = "gp3"
      delete_on_termination = true
    }
  }

  tag_specifications {
    resource_type = "instance"
    tags          = merge(local.common_tags, { Name = "${local.name}-app" })
  }

  tags = local.common_tags
}
