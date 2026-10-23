# Thăm dò dịch vụ AWS trên Free plan

Mục đích: biết dịch vụ nào ở `overview.md` §23.2 dùng được với tài khoản Free plan trước lab đầu tiên (Sprint 2). Mỗi dịch vụ một thư mục với state riêng, nên apply và destroy từng dịch vụ độc lập.

| Thư mục | Tạo gì | Lab sau này dùng |
|---|---|---|
| `ec2` | 1 instance t3.micro, gp3 8 GB, không IP công khai | Deploy ứng dụng (Sprint 2) |
| `spot` | 1 instance Spot (one-time) | k3s trên EC2 Spot (Sprint 6) |
| `rds` | RDS PostgreSQL 17 db.t4g.micro, Single-AZ | Deploy ứng dụng |
| `rds-multiaz` | Như trên, `multi_az = true` | Diễn tập failover RDS |
| `alb` | ALB + target group + listener trả 200 | Scale-out sau ALB |
| `asg` | Launch template + ASG + chính sách scale + CloudWatch alarm | Scale-out / scale-in |
| `cloudfront` | S3 private + CloudFront (chứng chỉ mặc định) | Host frontend |
| `ses` | Một danh tính email SES (sandbox), cần `-var email=...` | Gửi email thật |
| `cloudwatch-sns` | Log group 3 ngày + alarm + SNS gửi email, cần `-var email=...` | Cảnh báo |

SSM Parameter Store (standard) miễn phí và không cần thăm dò riêng.

## Trước khi chạy

1. Làm xong checklist bảo vệ tài khoản: [docs/ops/aws-account-protection.md](../../../../docs/ops/aws-account-protection.md) (MFA, Identity Center, budget 0,01$).
2. `aws sso login` rồi `aws sts get-caller-identity` để chắc đúng tài khoản. Không tạo access key dài hạn.
3. Cài Terraform ≥ 1.9 và AWS CLI v2.

## Chạy từng dịch vụ

```bash
cd infra/terraform/aws/probe
./run-probe.sh ec2
./run-probe.sh ses -var email=ban@example.com
```

Script làm theo thứ tự: hiển thị `terraform plan` → **hỏi bạn** có apply không → apply đúng bản plan đó → ghi kết quả vào `results/<dịch vụ>.txt` → **hỏi** có destroy ngay không (mặc định có) → chạy `verify-clean.sh`.

- `terraform apply` luôn do bạn xác nhận từng lần; tôi không tự chạy.
- Lỗi apply cũng là kết quả: nó cho biết dịch vụ bị chặn. Script vẫn đề nghị destroy để dọn phần đã tạo dở.
- Chạy một dịch vụ rồi destroy xong mới chạy dịch vụ kế tiếp, để chi phí và rủi ro dồn lại ở mức thấp nhất.
- Thứ tự gợi ý: `ec2` → `spot` → `rds` → `rds-multiaz` → `alb` → `asg` → `cloudfront` → `ses` → `cloudwatch-sns`.

## Sau mỗi lần

1. `./verify-clean.sh` phải báo `SẠCH`. Instance có thể còn `shutting-down` vài phút: chạy lại.
2. Chép kết quả từ `results/` vào bảng trong [docs/ops/aws-free-plan-probe.md](../../../../docs/ops/aws-free-plan-probe.md).
3. Hôm sau kiểm Cost Explorer (lọc theo tag `Project=tickethub`); kỳ vọng ~0$.

## Ghi chú chi phí

- Dùng VPC mặc định: không NAT Gateway, không Elastic IP, instance không có IP công khai.
- RDS: `skip_final_snapshot = true`, `backup_retention_period = 0`, gp3 20 GB. `rds-multiaz` nhân đôi giờ chạy: destroy ngay sau khi biết kết quả.
- ALB tính tiền theo giờ ngay cả khi không có traffic.
- CloudFront / S3 / SES / SNS gần như không phát sinh với lượng thử này.
- Mọi tài nguyên có tag `Project=tickethub`, `Lab=probe-<dịch vụ>`, `ExpiresAt=2026-12-11`.
