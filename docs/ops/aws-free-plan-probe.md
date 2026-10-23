# Kết quả thăm dò Free plan (AWS, ap-southeast-1)

Cách chạy: `infra/terraform/aws/probe/README.md`. Cột "Kết quả" do người dùng điền sau mỗi lần `run-probe.sh` (xem `results/<dịch vụ>.txt`). Chưa chạy thì để "Chưa chạy".

Giá trị mặc định của "Phương án thay thế" lấy từ `overview.md` §23.1, §23.3: dùng khi dịch vụ bị chặn.

| Dịch vụ | Dùng cho | Kết quả | Ngày chạy | Ghi chú lỗi / giới hạn | Phương án thay thế nếu bị chặn |
|---|---|---|---|---|---|
| EC2 On-Demand (t3.micro) | Deploy ứng dụng, Sprint 2 | Chưa chạy | | | Chạy Docker Compose trên máy dev, bỏ lab deploy |
| EC2 Spot | k3s ở Sprint 6 | Chưa chạy | | | EC2 On-Demand nhỏ (rút ngắn lab) hoặc k3d ở staging |
| RDS PostgreSQL Single-AZ | Deploy ứng dụng | Chưa chạy | | | Postgres trong container trên EC2 |
| RDS Multi-AZ | Diễn tập failover | Chưa chạy | | | Diễn tập failover bằng CNPG trên k3s |
| ALB | Scale-out sau load balancer | Chưa chạy | | | nginx trên EC2 làm load balancer |
| Auto Scaling Group + CloudWatch alarm | Scale-out / scale-in mức VM | Chưa chạy | | | Diễn tập scale bằng KEDA trên k3s |
| S3 + CloudFront | Host frontend | Chưa chạy | | | nginx trên EC2 |
| SES (sandbox) | Gửi email thật | Chưa chạy | | | Mailpit (dev) hoặc SMTP của nhà cung cấp khác |
| CloudWatch Logs + alarm + SNS → email | Cảnh báo | Chưa chạy | | | Alertmanager → email trên k3s |
| SSM Parameter Store (standard) | Cấu hình / secret | Không cần thăm dò (miễn phí) | | | |

## Sau khi chạy hết

- [ ] `./verify-clean.sh` báo SẠCH.
- [ ] Hôm sau Cost Explorer (tag `Project=tickethub`) ~0$.
- [ ] Cập nhật plan Sprint 2 theo cột "Kết quả" (mục nào bị chặn thì dùng phương án thay thế).
- [ ] AC-8 của Sprint 1: bảng này đủ kết quả và mọi tài nguyên thăm dò đã destroy.
