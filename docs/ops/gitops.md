# GitOps với ArgoCD (Sprint 3 việc 13)

Mọi thay đổi trên cụm đi qua Git; ArgoCD đồng bộ (`overview.md` §19.3). Lệnh `kubectl` / `helm` lên cụm cần báo trước.

## Luồng

```
push main ─▶ CI: test → build image → Trivy → đẩy GHCR (sha-<commit>)
          └─▶ job update-image-tags: sửa `tag:` trong infra/helm/{app,gateway,frontend}/values.yaml, commit [skip ci]
                       ▼
        ArgoCD (app-of-apps `root`) thấy commit → đồng bộ app / gateway / frontend vào k3d
```

## Thành phần

| Đường dẫn | Nội dung |
|---|---|
| `infra/argocd/root.yaml` | Application gốc, trỏ vào `infra/argocd/apps/` |
| `infra/argocd/apps/*.yaml` | Mỗi thành phần một Application; sync-wave: 0 operator (Envoy Gateway, cert-manager) → 1 core, network-policies → 2 app, gateway, frontend, cert-issuers → 3 edge |
| `infra/k8s-platform/gateway/` | GatewayClass, Gateway (TLS) và HTTPRoute: `/api`, `/oauth2`, `/login/oauth2`, `/logout` → Gateway BFF; còn lại → frontend; `auth.tickethub.localhost` → Keycloak |
| `infra/k8s-platform/cert-manager/` | CA tự ký cho local: ClusterIssuer `tickethub-ca` |
| `infra/k8s-platform/network-policies/` | Chặn mọi ingress mặc định; chỉ Envoy vào gateway / frontend / keycloak, chỉ gateway vào app, chỉ app và keycloak vào Postgres |

## Bootstrap (người dùng chạy, một lần)

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side -f https://raw.githubusercontent.com/argoproj/argo-cd/v3.1.5/manifests/install.yaml
# Secret của namespace tickethub: xem docs/ops/k8s-staging.md
kubectl apply -n argocd -f infra/argocd/root.yaml
```

Repo công khai nên ArgoCD đọc không cần credential. Thêm `tickethub.localhost` và `auth.tickethub.localhost` vào hosts của máy (trỏ 127.0.0.1) và cho trình duyệt tin CA `tickethub-ca`.

## Kiểm chứng

| Việc | Cách |
|---|---|
| Đổi tag → cụm cập nhật | Sau khi merge vào main, `kubectl -n tickethub get deploy app -o jsonpath='{.spec.template.spec.containers[0].image}'` đổi sang `sha-<commit>` |
| HTTPS qua Envoy | `curl --cacert ca.crt https://tickethub.localhost/` trả SPA |
| NetworkPolicy | `kubectl -n tickethub run probe --rm -it --image=busybox --labels=tickethub/role=other -- wget -T 3 -qO- http://app:8081/actuator/health` bị chặn |
| Trivy | Job `images` của CI (CRITICAL đã có bản vá → đỏ) |
| OWASP Dependency-Check | Job `dependency-check`, cần secret `NVD_API_KEY` |

## Rollback

Revert commit đổi tag (ArgoCD đồng bộ lại), hoặc `argocd app rollback`. Gỡ toàn bộ: xoá Application `root` (finalizer xoá các con).

## Rủi ro

- Job `update-image-tags` push thẳng vào `main`: bị từ chối nếu bật bảo vệ nhánh bắt buộc PR.
- Chưa chạy thật trên cụm; phiên bản chart Envoy Gateway / cert-manager và ArgoCD cần xác nhận lúc cài.
