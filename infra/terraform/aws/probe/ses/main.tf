# Thăm dò Free plan: SES: đăng ký một danh tính email (sandbox).
# Chạy bằng infra/terraform/aws/probe/run-probe.sh ses; xem README.md ở thư mục cha.

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
      Lab       = "probe-ses"
      ExpiresAt = var.expires_at
    }
  }
}

variable "email" {
  description = "Địa chỉ email thật để SES gửi thư xác minh (sandbox)."
  type        = string
}

resource "aws_ses_email_identity" "probe" {
  email = var.email
}

output "identity_arn" {
  value = aws_ses_email_identity.probe.arn
}
