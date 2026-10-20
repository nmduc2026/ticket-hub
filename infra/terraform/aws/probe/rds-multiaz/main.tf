# Thăm dò Free plan: RDS PostgreSQL 17 db.t4g.micro, Multi-AZ (diễn tập failover).
# Chạy bằng infra/terraform/aws/probe/run-probe.sh rds-multiaz; xem README.md ở thư mục cha.

terraform {
  required_version = ">= 1.9"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }
}

variable "region" {
  description = "Region duy nhất của dự án (overview.md §23.1)."
  type        = string
  default     = "ap-southeast-1"
}

variable "expires_at" {
  description = "Ngày phải gỡ xong mọi tài nguyên AWS (overview.md §23.1)."
  type        = string
  default     = "2026-12-11"
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "tickethub"
      Lab       = "probe-rds-multiaz"
      ExpiresAt = var.expires_at
    }
  }
}

# Dùng VPC mặc định: không tạo VPC riêng, không NAT Gateway, không Elastic IP (overview.md §23.3).
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_db_subnet_group" "probe" {
  name       = "tickethub-probe-rds-multiaz"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "probe" {
  identifier     = "tickethub-probe-rds-multiaz"
  engine         = "postgres"
  engine_version = "17"
  instance_class = "db.t4g.micro"
  multi_az       = true

  allocated_storage = 20
  storage_type      = "gp3"

  db_name  = "tickethub"
  username = "tickethub"
  password = random_password.db.result

  db_subnet_group_name = aws_db_subnet_group.probe.name
  publicly_accessible  = false

  # Lab: không giữ gì sau khi gỡ (overview.md §23.4 lớp 3).
  skip_final_snapshot     = true
  backup_retention_period = 0
  deletion_protection     = false
  apply_immediately       = true
}

output "endpoint" {
  value = aws_db_instance.probe.endpoint
}
