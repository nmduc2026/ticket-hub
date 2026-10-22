# Autoscaling, tắt êm và SSE qua Envoy (Sprint 7)

Thiết kế: `overview.md` §12, §15.3, §17; ADR 0028. Trạng thái: **đã viết, chưa chạy trên cụm** (cần k3d, hạng mục 13–14 của
sprint dọn dẹp).

## Thành phần

| Thành phần | File | Việc |
|---|---|---|
| KEDA | `infra/argocd/apps/keda.yaml` | Cài operator (chart `keda`, version chưa xác nhận trên mạng) |
| ScaledObject | `infra/k8s-platform/autoscaling/scaledobjects.yaml` | `app`, `gateway`: CPU 70 %, request/giây, p95 (app), request đang xử lý (gateway), pre-warm |
| Pre-warm | metric `tickethub_events_opening_soon` | Có sự kiện mở bán trong 15 phút tới → tối thiểu 2 bản sao |
| Tắt êm | `infra/helm/tickethub-lib/templates/_deployment.tpl` | `preStop` ngủ 10 giây (chờ gỡ khỏi Service), `terminationGracePeriodSeconds` 45, ứng dụng chờ request tối đa 30 giây |
| Envoy | `infra/k8s-platform/gateway/policies.yaml` | ALPN `h2`, bỏ timeout request cho SSE |
| Gateway metric | ServiceMonitor `tickethub-gateway` | Gateway xuất `/actuator/prometheus` |

## Cần xác nhận khi có cụm

1. Version chart KEDA 2.17.2 và địa chỉ Prometheus `kube-prometheus-stack-prometheus.monitoring.svc:9090`.
2. `lifecycle.preStop.sleep` cần Kubernetes ≥ 1.30 (image distroless không có lệnh `sleep`).
3. Tên trường `ClientTrafficPolicy` / `BackendTrafficPolicy` của Envoy Gateway v1.5.1: `kubectl apply --dry-run=server`.
4. Spring Cloud Gateway Server MVC có chuyển SSE không buffer không: mở `/api/v1/events/{id}/seats/stream` qua Gateway và
   xem `event:` tới từng cái một; nếu bị gom thì thêm cấu hình flush ở Gateway.
5. NetworkPolicy `allow-monitoring-ingress` cho Prometheus scrape cổng 8080 của `gateway`.

## Kiểm chứng (AC-10, AC-11)

- Tạo sự kiện mở bán sau 10 phút → `kubectl get hpa -n tickethub -w`: replica tăng trước giờ mở bán, về mức thường sau khi hết sự kiện sắp mở.
- Rolling restart lúc đang chạy Load: không có 5xx ngoài `503 OVERLOADED`; client SSE tự nối sang pod khác.

## Rollback

Xoá `autoscaling.yaml` / `keda.yaml` khỏi `infra/argocd/apps/` (ArgoCD prune); Deployment về `replicaCount` trong chart.
