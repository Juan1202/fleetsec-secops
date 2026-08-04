# FleetSec · security-baseline · VPC
# 3-tier (public/app/data) en >=2 AZs. NACL de datos deny-by-default. Flow Logs a S3 →
# el egreso anómalo (los 45.7 GB a Tor del breach del Sprint 4) queda registrado.

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = merge(local.common_tags, { Name = "${local.name}-vpc" })
}

# Default SG sin reglas (CIS 5.4 / CKV2_AWS_12).
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.common_tags, { Name = "${local.name}-default-DO-NOT-USE" })
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.common_tags, { Name = "${local.name}-igw" })
}

# ---- Subredes 3-tier ----
resource "aws_subnet" "public" {
  for_each                = { for idx, az in var.azs : az => idx }
  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, local.tier_offset.public + each.value)
  map_public_ip_on_launch = false
  tags                    = merge(local.common_tags, { Name = "${local.name}-public-${each.key}", Tier = "public" })
}

resource "aws_subnet" "app" {
  for_each          = { for idx, az in var.azs : az => idx }
  vpc_id            = aws_vpc.main.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, local.tier_offset.app + each.value)
  tags              = merge(local.common_tags, { Name = "${local.name}-app-${each.key}", Tier = "app" })
}

resource "aws_subnet" "data" {
  for_each          = { for idx, az in var.azs : az => idx }
  vpc_id            = aws_vpc.main.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, local.tier_offset.data + each.value)
  tags              = merge(local.common_tags, { Name = "${local.name}-data-${each.key}", Tier = "data" })
}

# ---- NAT para egreso del tier app (una por la primera AZ) ----
resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = merge(local.common_tags, { Name = "${local.name}-nat-eip" })
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[var.azs[0]].id
  tags          = merge(local.common_tags, { Name = "${local.name}-nat" })
  depends_on    = [aws_internet_gateway.main]
}

# ---- Route tables ----
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = merge(local.common_tags, { Name = "${local.name}-rt-public" })
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "app" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }
  tags = merge(local.common_tags, { Name = "${local.name}-rt-app" })
}

resource "aws_route_table_association" "app" {
  for_each       = aws_subnet.app
  subnet_id      = each.value.id
  route_table_id = aws_route_table.app.id
}

# Tier data: SIN ruta a internet (aislado).
resource "aws_route_table" "data" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.common_tags, { Name = "${local.name}-rt-data" })
}

resource "aws_route_table_association" "data" {
  for_each       = aws_subnet.data
  subnet_id      = each.value.id
  route_table_id = aws_route_table.data.id
}

# ---- NACL tier data: deny-by-default, solo 5432 desde app ----
resource "aws_network_acl" "data" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = [for s in aws_subnet.data : s.id]
  tags       = merge(local.common_tags, { Name = "${local.name}-nacl-data" })
}

resource "aws_network_acl_rule" "data_ingress_pg" {
  count          = length(var.azs)
  network_acl_id = aws_network_acl.data.id
  rule_number    = 100 + count.index
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = aws_subnet.app[var.azs[count.index]].cidr_block
  from_port      = 5432
  to_port        = 5432
}

resource "aws_network_acl_rule" "data_egress_ephemeral" {
  count          = length(var.azs)
  network_acl_id = aws_network_acl.data.id
  rule_number    = 100 + count.index
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = aws_subnet.app[var.azs[count.index]].cidr_block
  from_port      = 1024
  to_port        = 65535
}

# ---- Flow Logs → S3 (visibilidad del egreso anómalo) ----
resource "aws_flow_log" "s3" {
  vpc_id               = aws_vpc.main.id
  log_destination      = aws_s3_bucket.logs.arn
  log_destination_type = "s3"
  traffic_type         = "ALL"
  tags                 = merge(local.common_tags, { Name = "${local.name}-flowlogs" })
}
