# Log tập trung (ELK) trên staging

Elasticsearch 1 node, Kibana và Filebeat chạy bằng ECK (Elastic Cloud on Kubernetes) ở namespace `logging` (`overview.md` §16.1, ADR 0010). Triển khai qua ArgoCD: app `eck-operator` (wave 0) rồi `logging` (wave 3).

**Trạng thái:** manifest đã viết, chưa chạy trên cụm (chờ k3d, mục "Việc treo của Sprint 5" ở `docs/plans/2026-10-21-sprint-cleanup-plan.md`). Version chart `eck-operator` 3.1.0 và Elastic Stack 9.1.4 theo trí nhớ: kiểm bằng `helm search repo elastic/eck-operator --versions` và đổi `version` ở `elasticsearch.yaml`, `kibana.yaml`, `filebeat.yaml` cho khớp nhau.

## Dùng

```bash
# Mật khẩu người dùng elastic do ECK tạo
kubectl -n logging get secret tickethub-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d
kubectl -n logging port-forward svc/tickethub-kb-http 5601   # https://localhost:5601
```

Kibana → Discover, data view `filebeat-*` (tạo một lần, trường thời gian `@timestamp`). Tìm toàn bộ log của một lần mua vé:

```
traceId : "4bf92f3577b34da6a3ce929d0e0e4736"
bookingId : "0192f..."
```

`traceId` lấy từ Jaeger hoặc từ trường `traceId` trong response lỗi (Problem Details). Log của Gateway và app cùng `traceId` nên một truy vấn ra cả hai.

## Vòng đời index

Filebeat tạo policy `tickethub-logs`: rollover mỗi ngày, xoá sau 7 ngày. Kiểm: `GET _ilm/explain/<index>?human` trong Dev Tools, hoặc `GET _ilm/policy/tickethub-logs`.

## Chưa làm

- Link từ Grafana và Jaeger sang Kibana theo `traceId` (cần cấu hình giao diện của Jaeger v2 và data link của dashboard).
- Chạy thật và kiểm AC-11 của Sprint 5 (tìm được log của một lần mua vé, ILM xoá index cũ).
