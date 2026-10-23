# Lab AWS 1 (Sprint 2): chạy bản Sprint 1 trên AWS lần đầu.
#   CloudFront ─┬─ /            → S3 (frontend)
#               └─ /api/* /oauth2/* /login/* /logout /auth/* → EC2 (nginx → gateway / Keycloak) → RDS Postgres
# Xem README.md. Tạo bằng `terraform apply` (người dùng xác nhận từng lần), xong thì `terraform destroy` rồi
# `./verify-clean.sh` (overview.md §23.4). Không có tài nguyên thường trực.

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

variable "github_repository" {
  description = "Repo GitHub chứa image trên GHCR, dạng `chu-so-huu/ten-repo` (CI đặt tên image `ghcr.io/<repo>-app`, `-gateway`)."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "Dạng đúng: chu-so-huu/ten-repo."
  }
}

variable "image_tag" {
  description = "Tag image Sprint 1 trên GHCR (`main` hoặc `sha-<commit>`)."
  type        = string
  default     = "main"
}

variable "ghcr_username" {
  description = "Chỉ cần khi image trên GHCR là private: tên người dùng GitHub."
  type        = string
  default     = ""
}

variable "ghcr_token" {
  description = "Chỉ cần khi image là private: PAT quyền read:packages. Nằm trong state local (gitignore); truyền bằng TF_VAR_ghcr_token."
  type        = string
  default     = ""
  sensitive   = true
}

variable "instance_type" {
  description = "Keycloak + app + gateway + Redis cần ~3 GB RAM. Free plan chặn kiểu này thì thử `c7i-flex.large`."
  type        = string
  default     = "t3.medium"
}

variable "use_spot" {
  description = "Chạy EC2 bằng Spot (rẻ hơn, có thể bị thu hồi). Mặc định tắt cho buổi lab ngắn cần ổn định."
  type        = bool
  default     = false
}

variable "seed_data" {
  description = "Nạp dữ liệu mẫu (profile `seed`: organizer, sự kiện, sơ đồ ghế) để có gì xem sau khi đăng nhập."
  type        = bool
  default     = true
}

variable "auto_stop_hour" {
  description = "EventBridge Scheduler stop EC2 vào giờ này (giờ Việt Nam) phòng quên destroy (overview.md §23.4 lớp 3). -1 để tắt."
  type        = number
  default     = 23
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "tickethub"
      Lab       = "lab1"
      ExpiresAt = var.expires_at
    }
  }
}

# Dùng VPC mặc định: không VPC riêng, không NAT Gateway, không Elastic IP (overview.md §23.3).
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "random_id" "suffix" {
  byte_length = 3
}

locals {
  name   = "tickethub-lab1"
  suffix = random_id.suffix.hex
  # Tên image do CI đặt: ghcr.io/<repo viết thường>-<app|gateway> (.github/workflows/ci.yml).
  image_base = "ghcr.io/${lower(var.github_repository)}"
}
