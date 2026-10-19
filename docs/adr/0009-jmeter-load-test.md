# 0009. Load test: JMeter

**Trạng thái:** Accepted

## Bối cảnh
Cần chứng minh bất biến không oversell dưới tải (500 user tranh 100 ghế), đo SLO latency và tìm giới hạn hệ thống. Mục tiêu học là kỹ năng dùng được khi đi làm.

## Các phương án
- **JMeter:** phổ biến nhất ở doanh nghiệp Việt Nam, có GUI để thiết kế kịch bản, chạy CLI được.
- **k6:** viết kịch bản bằng JavaScript, nhẹ, hợp CI.

## Quyết định
**JMeter** là công cụ chính: thiết kế bằng GUI, chạy bằng CLI (`jmeter -n`), xuất HTML report. Máy 16 GB chạy JMeter bắn sang máy 32 GB (staging) qua LAN để tách máy tạo tải và máy chịu tải. k6 giữ làm tuỳ chọn cho CI.

Load test không gọi Stripe thật (test mode có rate limit và Stripe không cho load test): dùng stripe-mock / WireMock, webhook giả ký đúng chữ ký.

## Hệ quả
- (+) Kỹ năng sát thực tế tuyển dụng.
- (−) File `.jmx` dạng XML, khó review trong PR hơn kịch bản k6.

Liên quan: `overview.md` §19.1, §20.2.
