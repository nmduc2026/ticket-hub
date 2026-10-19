# 0017. Môi trường: local là chính, AWS là lab tạm thời

**Trạng thái:** Accepted

## Bối cảnh
- Chi phí ~0$. Tài khoản AWS Free plan, không nâng cấp: không bị trừ thẻ nhưng có thể bị chặn một số dịch vụ, và tài khoản đóng khi hết credits (khoảng 22/01/2027).
- Dự án dừng hẳn sau 18/12/2026.
- Có hai máy: 16 GB (dev) và 32 GB (staging).

## Các phương án
- **Môi trường cloud thường trực:** gần thực tế nhất, nhưng tốn tiền liên tục, dễ bị Free plan chặn, và mất khi tài khoản đóng.
- **Local là chính, AWS chỉ cho lab ngắn:** dev chạy Docker Compose, staging chạy k3d; AWS tạo bằng Terraform, làm lab xong thì destroy.

## Quyết định
**Local là chính.**
- dev: Docker Compose theo profile; staging: k3d trên máy 32 GB; aws-lab: Terraform tạo rồi destroy ngay.
- Không có tài nguyên AWS thường trực.
- Mọi tài nguyên AWS gỡ xong trước 11/12/2026, chừa ~35 ngày kiểm Cost Explorer trước khi hết credits.
- Sprint 1 chạy Terraform thăm dò từng dịch vụ để biết Free plan cho dùng gì; dịch vụ bị chặn thì dùng phương án thay thế.

## Hệ quả
- (+) Chi phí gần 0; lab vận hành (chaos, load test, PITR) chạy thoải mái trên staging.
- (−) Không có môi trường "production" luôn chạy; kỹ năng cloud chỉ học qua các buổi lab.
- (−) Backup Postgres không dùng S3 của AWS, mà dùng object storage tương thích S3 chạy trên staging.

Liên quan: `overview.md` §18, §19.1, §23, ADR 0013.
