# Chạy Ticket Hub trên k3d (staging)

Phạm vi Sprint 3 việc 12–13. Mọi lệnh `kubectl` / `helm` lên cụm cần báo trước (CLAUDE.md); file này chỉ ghi cách làm.

## Thành phần

| Thư mục | Nội dung |
|---|---|
| `infra/helm/tickethub-lib` | Library chart: Deployment (3 probe, resources, non-root, root filesystem chỉ đọc), Service, PodDisruptionBudget |
| `infra/helm/app`, `gateway`, `frontend` | Chart mỏng, chỉ có values; tag image do CI cập nhật |
| `infra/helm/core` | Postgres, Redis, Keycloak, Mailpit, S3Mock (bản đơn giản; CNPG ở Sprint 4) |
| `infra/k3d/cluster.yaml` | Cụm 1 server + 2 agent, tắt Traefik |

## Dựng lần đầu (người dùng chạy)

```bash
k3d cluster create --config infra/k3d/cluster.yaml
kubectl create namespace tickethub

# Secret: đặt giá trị thật tại máy, không commit.
kubectl -n tickethub create secret generic tickethub-db --from-literal=password=<...>
kubectl -n tickethub create secret generic tickethub-redis --from-literal=password=<...>
kubectl -n tickethub create secret generic tickethub-keycloak \
  --from-literal=admin-user=admin --from-literal=admin-password=<...> \
  --from-literal=bff-client-secret=<...> --from-literal=internal-client-secret=<...> \
  --from-literal=account-admin-client-secret=<...>
kubectl -n tickethub create secret generic tickethub-stripe \
  --from-literal=secret-key=sk_test_<...> --from-literal=webhook-secret=whsec_<...>
kubectl -n tickethub create configmap keycloak-realm --from-file=infra/keycloak/tickethub-realm.json

helm upgrade --install core infra/helm/core -n tickethub
for c in app gateway frontend; do helm dependency build infra/helm/$c; helm upgrade --install $c infra/helm/$c -n tickethub; done
```

Sau việc 13, bốn lệnh `helm upgrade` thay bằng ArgoCD (xem `docs/ops/gitops.md`).

## Kiểm chứng đã chạy (không cần cụm)

`helm lint` và `helm template` sạch cho cả 5 chart.

## Chưa kiểm chứng trên cụm

- App và Gateway khởi động, probe `readiness` lên.
- Đăng nhập: issuer của Keycloak là URL công khai (`https://auth.tickethub.localhost/...`), Gateway trong cụm phải phân giải được tên này (CoreDNS rewrite trỏ về Envoy Gateway, làm ở việc 13).
- Giới hạn bộ nhớ app (768Mi) và thời gian khởi động.

## Rollback

`helm uninstall <release> -n tickethub`; xoá cụm: `k3d cluster delete tickethub` (mất dữ liệu PVC).
