# 0010. Log tập trung: ELK

**Trạng thái:** Accepted

## Bối cảnh
Cần tìm log theo `traceId`, `bookingId`, `userId` xuyên Gateway và app để chẩn đoán sự cố, viết runbook và postmortem.

## Các phương án
- **Loki + Grafana:** nhẹ, rẻ, chỉ index nhãn; tìm theo nội dung log kém hơn.
- **ELK (Elasticsearch, Filebeat, Kibana):** tìm kiếm full-text mạnh, phổ biến; dùng chung Elasticsearch với tìm kiếm sự kiện (ADR 0006).

## Quyết định
**ELK.** App ghi Logback JSON (có `traceId`, `spanId`, `userId`, `bookingId`; không log thẻ, token) → Filebeat → Elasticsearch → Kibana. ILM xoá log sau 7 ngày. Logstash là tuỳ chọn.

## Hệ quả
- (+) Tìm log theo bất kỳ trường nào; học một hệ thống phổ biến ở doanh nghiệp.
- (−) Elasticsearch tốn RAM: trên máy dev, profile `observability` phải bật kèm `search`; đủ bộ chỉ chạy trên staging.

Liên quan: `overview.md` §16.1, §19.2, ADR 0006.
