# Thăm dò Free plan: Auto Scaling Group (launch template t3.micro) + chính sách scale + CloudWatch alarm.
# Chạy bằng infra/terraform/aws/probe/run-probe.sh asg; xem README.md ở thư mục cha.

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
      Lab       = "probe-asg"
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

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_launch_template" "probe" {
  name_prefix   = "tickethub-probe-"
  image_id      = data.aws_ssm_parameter.al2023.value
  instance_type = "t3.micro"

  metadata_options {
    http_tokens = "required"
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_type           = "gp3"
      volume_size           = 8
      delete_on_termination = true
    }
  }
}

resource "aws_autoscaling_group" "probe" {
  name                = "tickethub-probe-asg"
  min_size            = 0
  desired_capacity    = 1
  max_size            = 2
  vpc_zone_identifier = slice(data.aws_subnets.default.ids, 0, 2)

  launch_template {
    id      = aws_launch_template.probe.id
    version = "$Latest"
  }

  # Cho phép `terraform destroy` xoá ASG cùng instance, không chờ drain.
  force_delete = true
}

resource "aws_autoscaling_policy" "scale_out" {
  name                   = "tickethub-probe-scale-out"
  autoscaling_group_name = aws_autoscaling_group.probe.name
  policy_type            = "SimpleScaling"
  adjustment_type        = "ChangeInCapacity"
  scaling_adjustment     = 1
  cooldown               = 60
}

resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  alarm_name          = "tickethub-probe-cpu-high"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 2
  threshold           = 70
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_autoscaling_policy.scale_out.arn]

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.probe.name
  }
}
