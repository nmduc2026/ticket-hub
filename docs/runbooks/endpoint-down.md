# Runbook: endpoint không phản hồi (alert `EndpointDown`)

Blackbox exporter gọi `app`, `gateway`, `frontend` mỗi phút; `probe_success == 0` quá 2 phút.

1. `kubectl -n tickethub get pods` và `describe pod` của workload tương ứng (CrashLoop, probe fail, OOMKilled).
2. `kubectl -n tickethub logs deploy/<tên> --tail=200`.
3. Phụ thuộc: `app` cần Postgres (`kubectl cnpg status tickethub-pg -n tickethub`) và Keycloak; `gateway` cần Keycloak và Redis.
4. NetworkPolicy: probe đi từ namespace `monitoring`, cần `allow-monitoring-ingress` (`infra/k8s-platform/observability/network-policy.yaml`).
5. Vừa deploy: `kubectl -n tickethub rollout undo deploy/<tên>` (báo trước).
