# Bucket ảnh banner / avatar dùng chung cho dev và staging. Khác lab1 / probe: tài nguyên thường trực, không destroy
# sau mỗi buổi lab. Ảnh đọc thẳng bằng URL S3 (public-read); CloudFront thêm ở Sprint 6 hạng mục 8.
# Tạo bằng `terraform apply` (người dùng xác nhận từng lần), xem README.md.

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

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project = "tickethub"
      Stack   = "media"
    }
  }
}

data "aws_caller_identity" "current" {}

# Tên bucket là duy nhất toàn cầu: thêm account id cho chắc không trùng.
resource "aws_s3_bucket" "media" {
  bucket = "tickethub-media-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id

  # Cho phép bucket policy public-read bên dưới; ACL vẫn chặn.
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "public_read" {
  bucket     = aws_s3_bucket.media.id
  depends_on = [aws_s3_bucket_public_access_block.media]

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadObjects"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.media.arn}/*"
    }]
  })
}

# Khoá cho app chạy ở dev / staging (không có IAM role): chỉ đọc / ghi / xoá đối tượng trong bucket này.
resource "aws_iam_user" "app" {
  name = "tickethub-media-app"
}

resource "aws_iam_user_policy" "app" {
  name = "tickethub-media-objects"
  user = aws_iam_user.app.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
      Resource = "${aws_s3_bucket.media.arn}/*"
    }]
  })
}

resource "aws_iam_access_key" "app" {
  user = aws_iam_user.app.name
}

output "storage_bucket" {
  value = aws_s3_bucket.media.bucket
}

output "storage_public_base_url" {
  value = "https://${aws_s3_bucket.media.bucket_regional_domain_name}"
}

output "storage_access_key" {
  value = aws_iam_access_key.app.id
}

# Nằm trong state local (gitignore). Đọc bằng `terraform output -raw storage_secret_key`.
output "storage_secret_key" {
  value     = aws_iam_access_key.app.secret
  sensitive = true
}
