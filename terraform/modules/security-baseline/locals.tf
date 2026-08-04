# FleetSec · security-baseline · locals y data sources

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition
  region     = var.aws_region
  name       = var.env

  common_tags = merge(var.tags, {
    Environment = var.env
    Region      = var.aws_region
  })

  # Subredes 3-tier derivadas del CIDR: /24 por tier por AZ.
  # public = 0..N, app = 10..N, data = 20..N (offsets por tier).
  tier_offset = {
    public = 0
    app    = 10
    data   = 20
  }
}
