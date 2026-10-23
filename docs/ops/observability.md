# Observability trên staging (metric, alert, tracing)

Thiết kế đích: `overview.md` §16. Mọi lệnh `kubectl`/`helm` lên cụm cần báo trước (`CLAUDE.md`).

## Thành phần (ArgoCD, `infra/argocd/apps/`)

| App | Nội dung | Wave |
|---|---|---|
| `kube-prometheus-stack` | Prometheus, Alertmanager, Grafana, operator (namespace `monitoring`) | 1 |
| `blackbox-exporter` | Probe HTTP | 2 |
| `observability` | `infra/k8s-platform/observability/`: ServiceMonitor app, PrometheusRule (bất biến, burn rate, nền tảng), dashboard, Probe, NetworkPolicy, chứng chỉ diễn tập C9, Jaeger all-in-one | 3 |

cert-manager bật `ServiceMonitor` để có `certmanager_*`; CNPG bật `enablePodMonitor`.

## Secret tạo tay (không commit)

```bash
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
kubectl -n monitoring create secret generic alertmanager-telegram --from-literal=token='<bot token từ BotFather>'
kubectl -n monitoring create secret generic grafana-admin --from-literal=admin-user=admin --from-literal=admin-password='<mật khẩu>'
```

Sau đó sửa `chat_id` (số) trong `kube-prometheus-stack.yaml` rồi commit.

## Kiểm chứng

| Việc | Cách | Kỳ vọng |
|---|---|---|
| Prometheus scrape app | Prometheus UI → Targets | `serviceMonitor/monitoring/tickethub-app` UP |
| Alert tới Telegram (AC-12) | `kubectl -n monitoring create configmap alert-drill` | Sau ~1 phút có tin `DrillInvariant` kèm Runbook; xoá ConfigMap để resolve |
| Alert bất biến thật | Theo từng runbook trong `docs/runbooks/` | Gauge/counter tăng thì alert bắn |
| C9 | `runbooks/certificate-expiry.md` | Chứng chỉ 1 giờ tự gia hạn |

## Tracing và Sentry

- `gateway` và `app` xuất trace OTLP/HTTP tới `http://jaeger.monitoring.svc:4318/v1/traces` khi `OTLP_TRACES_ENABLED=true` (đặt trong `values.yaml` của chart; mặc định tắt để dev/test không cần Jaeger). Gateway gửi header `traceparent`, app nối vào cùng trace; `traceId` có trong log JSON (test `JsonLoggingTests`).
- Xem trace: `kubectl -n monitoring port-forward svc/jaeger 16686`, mở http://localhost:16686, service `gateway` / `app`. Jaeger lưu trong bộ nhớ nên restart là mất.
- Sentry (FE): đặt `VITE_SENTRY_DSN` khi build image (`--build-arg`); trống thì FE không gửi gì. CSP nginx cho phép `*.ingest.sentry.io`.
- Kiểm: mua một vé rồi tìm trace theo `traceId` trong log của `app`; để thử Sentry, ném lỗi giả trong một route rồi xem sự kiện trong project Sentry.

## Rollback

Xoá Application trong `infra/argocd/apps/` (prune sẽ gỡ). CRD của operator không tự xoá: `kubectl delete crd` thủ công nếu cần.
