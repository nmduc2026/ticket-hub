# Mọi secret sinh ngẫu nhiên, cất ở SSM Parameter Store (standard, miễn phí) dạng SecureString; EC2 đọc lúc khởi động
# bằng IAM role. Giá trị cũng nằm trong state local của Terraform (gitignore), nên không dùng lại ngoài lab này.

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "random_password" "redis" {
  length  = 24
  special = false
}

resource "random_password" "keycloak_admin" {
  length  = 24
  special = false
}

resource "random_password" "bff_secret" {
  length  = 32
  special = false
}

resource "random_password" "internal_secret" {
  length  = 32
  special = false
}

resource "random_password" "account_admin_secret" {
  length  = 32
  special = false
}

locals {
  secrets = merge({
    db                   = random_password.db.result
    redis                = random_password.redis.result
    keycloak-admin       = random_password.keycloak_admin.result
    bff-secret           = random_password.bff_secret.result
    internal-secret      = random_password.internal_secret.result
    account-admin-secret = random_password.account_admin_secret.result
  }, var.ghcr_token != "" ? { ghcr-token = var.ghcr_token } : {})
}

resource "aws_ssm_parameter" "secret" {
  for_each = nonsensitive(toset(keys(local.secrets)))

  name  = "/${local.name}/${each.key}"
  type  = "SecureString"
  value = local.secrets[each.key]
}

# Địa chỉ công khai (domain CloudFront) chỉ biết sau khi tạo distribution, mà distribution lại cần địa chỉ EC2:
# để không vòng tròn, EC2 chờ tham số này xuất hiện rồi mới dựng cấu hình.
resource "aws_ssm_parameter" "public_url" {
  name  = "/${local.name}/public-url"
  type  = "String"
  value = "https://${aws_cloudfront_distribution.main.domain_name}"
}
