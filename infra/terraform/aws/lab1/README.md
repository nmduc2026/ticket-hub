# Lab AWS 1: chạy bản Sprint 1 trên AWS

Mục tiêu (Sprint 2, việc 15): mở frontend qua CloudFront, đăng nhập được bằng user test, rồi gỡ sạch.

```
Trình duyệt ── CloudFront ─┬─ /                       → S3 (frontend, riêng tư, OAC)
                           └─ /api/* /oauth2/* /login/* /logout /auth/*
                                    └→ EC2 (nginx → gateway → app | Keycloak, Redis) ── RDS PostgreSQL 17
```

Cùng một domain `*.cloudfront.net` cho frontend, API và Keycloak (`/auth`), nên cookie SESSION của Gateway và luồng OIDC chạy như ở dev. TLS kết thúc ở CloudFront; CloudFront gọi EC2 bằng HTTP cổng 80, Security Group chỉ nhận từ danh sách IP của CloudFront. Không SSH, không ALB, không NAT, không Elastic IP.

## Trước khi chạy

1. Checklist bảo vệ tài khoản (`docs/ops/aws-account-protection.md`) và kết quả thăm dò (`docs/ops/aws-free-plan-probe.md`): EC2, RDS, S3 + CloudFront nên đã chạy được. Dịch vụ nào bị chặn thì xem "Nếu Free plan chặn" bên dưới.
2. Image `ghcr.io/<repo>-app`, `-gateway` đã có trên GHCR (CI đẩy từ `main`). Image private thì đặt `TF_VAR_ghcr_username` và `TF_VAR_ghcr_token` (PAT quyền `read:packages`).
3. `aws sso login`; Terraform ≥ 1.9; Node để build frontend.

## Chạy

```bash
cd infra/terraform/aws/lab1
terraform init
terraform plan -var github_repository=<chu-so-huu>/<ten-repo>      # đọc kỹ plan
terraform apply -var github_repository=<chu-so-huu>/<ten-repo>     # luôn hỏi từng lần
./deploy-frontend.sh                                               # build + đẩy S3 + làm mới CloudFront
```

Chờ EC2 khởi động xong (~5–8 phút: cài Docker, kéo image, Keycloak import realm). Theo dõi:

```bash
aws ssm start-session --target $(terraform output -raw instance_id)
sudo tail -f /var/log/tickethub-bootstrap.log
sudo docker compose -f /opt/tickethub/compose.yaml ps
```

Mở `terraform output public_url`, bấm Đăng nhập, dùng user test của realm (`customer`, `organizer`, `admin`; mật khẩu như ở dev, xem `infra/keycloak/tickethub-realm.json`). Log container nằm ở CloudWatch Logs `/tickethub/lab1` (giữ 3 ngày).

## Gỡ

```bash
terraform destroy -var github_repository=<chu-so-huu>/<ten-repo>   # lab xong là destroy, không stop
./verify-clean.sh                                                  # phải báo SẠCH
```

Hôm sau xem Cost Explorer (tag `Project=tickethub`, `Lab=lab1`). EventBridge Scheduler tự **stop** EC2 lúc 23h giờ Việt Nam phòng quên (`-var auto_stop_hour=-1` để tắt); stop không thay được destroy.

## Chi phí ước tính cho buổi vài giờ

EC2 `t3.medium` ~0,05$/giờ, RDS `db.t4g.micro` ~0,02$/giờ, CloudFront và S3 gần 0 ở mức lab. Chạy xong destroy ngay; `ExpiresAt` mặc định 2026-12-11.

## Lưu ý bảo mật

- Realm dùng user test mật khẩu yếu: chỉ để trong buổi lab, destroy ngay sau đó. nginx chặn `/auth/admin` và `/auth/realms/master` nên giao diện quản trị Keycloak không ra Internet.
- Secret (DB, Redis, Keycloak, client secret) sinh ngẫu nhiên, cất ở SSM SecureString; chúng và PAT GHCR nằm trong `terraform.tfstate` local (đã gitignore). Không dùng lại ngoài lab.
- EC2 chỉ có quyền đọc đúng các tham số `/tickethub-lab1/*`, bucket artifacts và ghi log group của lab.

## Giới hạn đã biết

- Upload ảnh banner chưa chạy trên lab: app trỏ S3 giả mặc định, chưa có bucket media thật.
- Gateway kiểm `iss` của Keycloak nên gọi Keycloak qua địa chỉ công khai (đi vòng ra CloudFront khi khởi động); `app` lấy khoá ký qua mạng nội bộ.
- Hai container `app` và `gateway` (distroless) không có healthcheck trong compose; nginx chờ Keycloak sẵn sàng rồi chạy, vài chục giây đầu có thể trả 502.

## Nếu Free plan chặn

- Kiểu `t3.medium`: đặt `-var instance_type=c7i-flex.large` (kiểu được Free plan cho phép, 4 GB).
- Spot: mặc định tắt (`use_spot=false`).
- CloudFront / S3 bị chặn: dùng nginx trên EC2 phục vụ frontend (phương án thay thế ở bảng thăm dò) và bỏ `cdn.tf`.
- EventBridge Scheduler bị chặn: `-var auto_stop_hour=-1`.

## Chưa kiểm chứng

Mới chạy `terraform validate` và render thử `user_data`; chưa `plan` / `apply` thật vì cần tài khoản AWS. Lần apply đầu có thể lộ lỗi nhỏ (đường dẫn health Keycloak sau `/auth`, quyền IAM): sửa theo `/var/log/tickethub-bootstrap.log`.
