output "public_url" {
  description = "Địa chỉ mở frontend (sau khi chạy deploy-frontend.sh và chờ EC2 khởi động xong, ~5-8 phút)."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "frontend_bucket" {
  description = "Bucket S3 chứa frontend; deploy-frontend.sh đồng bộ vào đây."
  value       = aws_s3_bucket.frontend.id
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.main.id
}

output "instance_id" {
  description = "Vào máy bằng: aws ssm start-session --target <id>. Log khởi động: /var/log/tickethub-bootstrap.log."
  value       = aws_instance.main.id
}

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "log_group" {
  value = aws_cloudwatch_log_group.main.name
}
