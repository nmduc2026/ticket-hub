# Thăm dò Free plan: CloudWatch Logs giữ 3 ngày + alarm gửi SNS topic -> email.
# Chạy bằng infra/terraform/aws/probe/run-probe.sh cloudwatch-sns; xem README.md ở thư mục cha.

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
      Lab       = "probe-cloudwatch-sns"
      ExpiresAt = var.expires_at
    }
  }
}

variable "email" {
  description = "Địa chỉ email nhận cảnh báo thử (bấm xác nhận trong thư SNS gửi tới)."
  type        = string
}

resource "aws_cloudwatch_log_group" "probe" {
  name              = "/tickethub/probe"
  retention_in_days = 3
}

resource "aws_sns_topic" "alerts" {
  name = "tickethub-probe-alerts"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.email
}

resource "aws_cloudwatch_metric_alarm" "probe" {
  alarm_name          = "tickethub-probe-alarm"
  namespace           = "TicketHub/Probe"
  metric_name         = "Heartbeat"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
