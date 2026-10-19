# 0028. Autoscaling bằng KEDA

**Trạng thái:** Accepted

## Bối cảnh
Tải cực kỳ không đều: gần như không có traffic, rồi đột biến lúc mở bán. JVM khởi động chậm nên scale theo CPU sau khi tải đã tới là trễ.
- KEDA Cron scaler chỉ nhận lịch cron tĩnh, không đọc được `sale_starts_at` của từng sự kiện.
- KEDA tự tạo HPA; khai báo thêm HPA riêng trên cùng workload thì hai bên tranh nhau.
- Ở modular monolith mọi consumer chạy chung app, nên scale-to-zero consumer không áp dụng được.

## Các phương án
- **HPA cho API + KEDA Cron để pre-warm:** chồng hai bộ điều khiển trên một workload; cron không biết lịch mở bán.
- **KEDA cho mọi workload, pre-warm theo metric.**

## Quyết định
**KEDA cho mọi workload**, không khai báo HPA riêng.
- Gateway và app: trigger CPU + Prometheus (request/giây, p95 latency).
- Pre-warm: app tự xuất metric `events_opening_soon` (số sự kiện mở bán trong 15 phút tới); > 0 thì giữ số replica tối thiểu cao hơn.
- Consumer đã tách service: scale theo Kafka consumer lag, scale-to-zero khi không có message. Chỉ áp dụng sau khi tách service.

## Hệ quả
- (+) Một bộ điều khiển cho mỗi workload; pre-warm theo dữ liệu thật.
- (−) Thêm KEDA vào cluster; app phải tự xuất metric pre-warm.

Liên quan: `overview.md` §17, §20.3 (C5), ADR 0029.
