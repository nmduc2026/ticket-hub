# Thăm dò Free plan: Application Load Balancer + target group + listener trả 200.
# Chạy bằng infra/terraform/aws/probe/run-probe.sh alb; xem README.md ở thư mục cha.

terraform {
  required_version = ">= 1.9"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
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
      Lab       = "probe-alb"
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

resource "aws_security_group" "alb" {
  name_prefix = "tickethub-probe-alb-"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_lb" "probe" {
  name               = "tickethub-probe-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = slice(data.aws_subnets.default.ids, 0, 2)
}

resource "aws_lb_target_group" "probe" {
  name     = "tickethub-probe-tg"
  port     = 80
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id
}

resource "aws_lb_listener" "probe" {
  load_balancer_arn = aws_lb.probe.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "ok"
      status_code  = "200"
    }
  }
}

output "dns_name" {
  value = aws_lb.probe.dns_name
}
