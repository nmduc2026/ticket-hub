# EC2 chạy Docker Compose: nginx → gateway (BFF) → app, Keycloak, Redis. Cấu hình lấy từ S3 artifacts + SSM lúc khởi động.

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_caller_identity" "current" {}

resource "aws_cloudwatch_log_group" "main" {
  name              = "/tickethub/lab1"
  retention_in_days = 3
}

# ---- Artifacts (realm, theme, compose, nginx) ở bucket riêng tư; EC2 tải về lúc khởi động ----

resource "aws_s3_bucket" "artifacts" {
  bucket        = "${local.name}-artifacts-${local.suffix}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

locals {
  keycloak_dir = "${path.module}/../../../keycloak"
  artifacts = merge(
    {
      "compose.yaml"         = "${path.module}/files/compose.yaml"
      "nginx.conf"           = "${path.module}/files/nginx.conf"
      "tickethub-realm.json" = "${local.keycloak_dir}/tickethub-realm.json"
    },
    { for file in fileset("${local.keycloak_dir}/themes/tickethub", "**") : "themes/tickethub/${file}" => "${local.keycloak_dir}/themes/tickethub/${file}" },
  )
}

resource "aws_s3_object" "artifact" {
  for_each = local.artifacts

  bucket = aws_s3_bucket.artifacts.id
  key    = each.key
  source = each.value
  etag   = filemd5(each.value)
}

# ---- IAM: quyền tối thiểu cho EC2 ----

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "${local.name}-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "ec2_access" {
  statement {
    actions   = ["ssm:GetParameter"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/*"]
  }
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.artifacts.arn}/*"]
  }
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.artifacts.arn]
  }
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.main.arn}:*"]
  }
}

resource "aws_iam_role_policy" "ec2_access" {
  name   = "access"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2_access.json
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${local.name}-ec2"
  role = aws_iam_role.ec2.name
}

# ---- EC2 ----

resource "aws_instance" "main" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  # IP công khai tự cấp (không phải Elastic IP) để CloudFront gọi được origin mà không cần ALB.
  associate_public_ip_address = true

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    delete_on_termination = true
    encrypted             = true
  }

  dynamic "instance_market_options" {
    for_each = var.use_spot ? [1] : []
    content {
      market_type = "spot"
      spot_options {
        spot_instance_type             = "one-time"
        instance_interruption_behavior = "terminate"
      }
    }
  }

  user_data = templatefile("${path.module}/files/user_data.sh.tftpl", {
    region        = var.region
    name          = local.name
    artifacts     = aws_s3_bucket.artifacts.id
    log_group     = aws_cloudwatch_log_group.main.name
    db_host       = aws_db_instance.main.address
    app_image     = "${local.image_base}-app:${var.image_tag}"
    gateway_image = "${local.image_base}-gateway:${var.image_tag}"
    seed_profile  = var.seed_data ? "seed" : ""
    ghcr_username = var.ghcr_username
  })
  user_data_replace_on_change = true

  # Cấu hình phải có đủ trước khi máy khởi động và chạy script.
  depends_on = [aws_s3_object.artifact, aws_ssm_parameter.secret, aws_iam_role_policy.ec2_access]

  tags = {
    Name = local.name
  }
}

# ---- Tự stop EC2 theo giờ phòng quên destroy (lab xong vẫn phải destroy, không chỉ stop) ----

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  count              = var.auto_stop_hour >= 0 ? 1 : 0
  name               = "${local.name}-scheduler"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

resource "aws_iam_role_policy" "scheduler" {
  count = var.auto_stop_hour >= 0 ? 1 : 0
  name  = "stop-instance"
  role  = aws_iam_role.scheduler[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "ec2:StopInstances"
      Resource = "arn:aws:ec2:${var.region}:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.main.id}"
    }]
  })
}

resource "aws_scheduler_schedule" "stop" {
  count                        = var.auto_stop_hour >= 0 ? 1 : 0
  name                         = "${local.name}-stop"
  schedule_expression          = "cron(0 ${var.auto_stop_hour} * * ? *)"
  schedule_expression_timezone = "Asia/Ho_Chi_Minh"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler[0].arn
    input    = jsonencode({ InstanceIds = [aws_instance.main.id] })
  }
}
