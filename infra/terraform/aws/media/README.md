# Bucket media (dev + staging)

Một bucket S3 cho ảnh banner sự kiện và ảnh đại diện, dùng chung cho dev và staging. Đây là tài nguyên thường trực duy nhất trên AWS: không destroy sau mỗi buổi lab; `verify-clean.sh` của lab1 / probe bỏ qua bucket `tickethub-media-*`. Test tự động vẫn dùng S3Mock (Testcontainers), không cần khoá AWS.

## Tạo

```bash
aws sso login
cd infra/terraform/aws/media
terraform init
terraform apply          # người dùng xác nhận trước khi chạy
```

Tạo ra: bucket `tickethub-media-<account-id>` (đọc công khai từng đối tượng, không liệt kê được), IAM user `tickethub-media-app` chỉ có quyền đọc / ghi / xoá đối tượng trong bucket đó, và một access key.

## Đưa vào app

```bash
terraform output storage_bucket storage_public_base_url storage_access_key
terraform output -raw storage_secret_key
```

- Dev: điền `STORAGE_BUCKET`, `STORAGE_PUBLIC_BASE_URL`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` vào `infra/docker-compose/.env`.
- Staging: `STORAGE_BUCKET`, `STORAGE_PUBLIC_BASE_URL` trong `infra/helm/app/values.yaml`; khoá vào Secret `tickethub-storage` (`access-key`, `secret-key`), không commit.

## Gỡ

Xoá hết đối tượng rồi `terraform destroy`. Làm khi dừng dự án, hoặc trước hạn gỡ AWS (overview.md §23.1).
