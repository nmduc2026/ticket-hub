# Observability trên staging (metric, alert, tracing)

Thiết kế đích: `overview.md` §16. Mọi lệnh `kubectl`/`helm` lên cụm cần báo trước (`CLAUDE.md`).

## Thành phần (ArgoCD, `infra/argocd/apps/`)

| App | Nội dung | Wave |
|---|---|---|
| `kube-prometheus-stack` | Prometheus, Alertmanager, Grafana, operator (namespace `monitoring`) | 1 |
| `blackbox-exporter` | Probe HTTP | 2 |
| `observability` | `infra/k8s-platform/observability/`: ServiceMonitor app, PrometheusRule (bất biến, burn rate, nền tảng), dashboard, Probe, NetworkPolicy, chứng chỉ diễn tập C9 | 3 |
| `jaeger` | Jaeger all-in-one nhận OTLP (xem hạng mục 11) | 2 |

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

## Rollback

Xoá Application trong `infra/argocd/apps/` (prune sẽ gỡ). CRD của operator không tự xoá: `kubectl delete crd` thủ công nếu cần.
