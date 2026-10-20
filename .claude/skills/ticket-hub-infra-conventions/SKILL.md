---
name: ticket-hub-infra-conventions
description: "Quy tắc hạ tầng của Ticket Hub: Docker Compose theo profile, realm Keycloak, Dockerfile, GitHub Actions, Helm / Kubernetes (k3d, k3s), ArgoCD, KEDA, observability, Terraform cho AWS lab và kiểm soát chi phí, an toàn khi thao tác môi trường. Dùng khi viết, sửa hoặc review mọi thứ trong infra/, .github/, Dockerfile, hoặc khi chạy lệnh đụng tới cluster, AWS."
---

# Ticket Hub: quy tắc hạ tầng

Đọc kèm `ticket-hub-coding-conventions` (secret, cấu hình). Thiết kế đích: `overview.md` §15–§19, §23; ADR 0013, 0014, 0017, 0028.

## 1. An toàn trước hết

- Thao tác đụng môi trường thật (commit / push, deploy, `kubectl` / `helm` lên cluster, `terraform apply` / `destroy`, migration ngoài local) theo quy tắc "báo trước / skip" trong `CLAUDE.md`. `terraform apply` luôn hỏi từng lần.
- Mặc định chỉ chuẩn bị và dừng ở `validate`, `plan`, `--dry-run`, `helm template`, build image local.
- Không in, log hay commit secret. Thấy secret trong repo thì báo ngay là phát hiện Critical.
- Không tắt test, kiểm tra bảo mật hay gate để pipeline chạy qua.

## 2. Cấu trúc

```
infra/
├── docker-compose/   # compose.yaml + .env.example, theo profile
├── keycloak/         # realm export tickethub
├── helm/             # library chart + chart app, gateway, frontend
├── argocd/           # app-of-apps
├── k8s-platform/     # CNPG, KEDA, Prometheus, ELK, Chaos Mesh...
└── terraform/aws/<lab>/   # mỗi lab một thư mục + verify-clean.sh
```

## 3. Docker Compose (dev)

- Profile: `core` (Postgres, Redis, Keycloak, Mailpit), `kafka`, `search`, `observability`, `platform` (`overview.md` §19.2). Máy 16 GB chạy `core` + tối đa 1 profile.
- **Pin version image**, không dùng `latest`. Mọi service có `healthcheck`; `depends_on` dùng `condition: service_healthy`.
- Postgres: một database, **mỗi module một schema**. S3 khi dev dùng Adobe S3Mock.
- Biến môi trường trong `.env` (gitignore); commit `.env.example` với giá trị giả.
- Giới hạn RAM từng container để máy dev không bị treo.

## 4. Keycloak

- Realm `tickethub` export ở `infra/keycloak/`, import khi container khởi động; sửa realm qua UI thì export lại và commit.
- Client và role theo `overview.md` §13.2; audience mapper `tickethub-api`; `tickethub-account-admin` chỉ có `view-users`, `manage-users`.
- Client secret trong file export lấy từ biến môi trường, không commit secret thật. User test chỉ dùng ở dev.
- Thay đổi realm và quyền client phải nêu rõ trong báo cáo bàn giao.

## 5. Dockerfile

- Multi-stage; Spring Boot dùng layered JAR; base image tối giản (distroless / chiseled), pin version.
- Chạy **non-root**; có `.dockerignore`; không copy `.env` hay secret vào image.
- Frontend: build tĩnh, phục vụ bằng nginx (CSP đầy đủ đặt ở nginx, `overview.md` §13.1).

## 6. CI (GitHub Actions)

- Mỗi PR: backend build + unit + Testcontainers + Spotless; frontend lint, type-check, test; gitleaks; Trivy (khi đã có image).
- Pin version cho action; cache Maven và npm.
- Image đẩy lên **GHCR** chỉ từ `main`. Truy cập AWS bằng **OIDC role**, không lưu access key dài hạn.
- CI xanh là điều kiện merge (`CLAUDE.md`).

## 7. Kubernetes, Helm, ArgoCD

- Một library chart dùng chung; mỗi ứng dụng một chart mỏng. Kiểm bằng `helm lint` và `helm template` trước khi đẩy.
- Mỗi Deployment có: `startupProbe` / `readinessProbe` / `livenessProbe` (Actuator), `resources` requests + limits, `securityContext` non-root, `PodDisruptionBudget`.
- Ingress bằng **Gateway API + Envoy Gateway** (ADR 0014); TLS bằng cert-manager. NetworkPolicy: chỉ Gateway BFF nhận traffic từ ngoài; DB chỉ nhận từ app.
- Autoscaling bằng **KEDA**, không khai báo HPA riêng trên cùng workload (ADR 0028).
- Secret: K8s Secret (sau là External Secrets + Vault), không đặt trong `values.yaml`.
- Deploy theo **GitOps**: thay đổi qua Git, ArgoCD đồng bộ. `kubectl apply` tay chỉ dùng để bootstrap hoặc trong buổi lab, và phải báo trước.
- Postgres trên cluster dùng CloudNativePG; backup WAL + base backup lên object storage tương thích S3 chạy trên staging (không dùng S3 của AWS).

## 8. Observability

- Metric nghiệp vụ `tickethub.<module>.<metric>`; alert bất biến theo `overview.md` §16.3 là mức cao nhất.
- Mỗi alert có link tới runbook trong `docs/runbooks/`. Sự cố lớn hoặc chaos test có postmortem trong `docs/postmortems/`.
- Log JSON có `traceId`; ILM xoá log sau 7 ngày.

## 9. Terraform và AWS

- Region `ap-southeast-1`. Mỗi lab một thư mục, state local (gitignore), không có tài nguyên thường trực.
- Provider đặt `default_tags`: `Project=tickethub`, `Lab=<tên>`, `ExpiresAt=<ngày>`.
- Theo §23.4: RDS `skip_final_snapshot = true`; EBS `delete_on_termination = true`, gp3; CloudWatch Logs giữ 3–7 ngày; không NAT Gateway, không Elastic IP thừa; EC2 dùng Spot.
- Quy trình: `terraform plan` → người dùng xem plan → `apply` (hỏi từng lần) → làm lab → `destroy` → `verify-clean.sh` → hôm sau kiểm Cost Explorer.
- Mọi tài nguyên gỡ xong trước **11/12/2026**.

## 10. Quy trình làm task hạ tầng

1. Đọc hạng mục trong plan, các § overview và ADR được nêu, cấu hình hiện có.
2. Thay đổi lớn: ghi ngắn trong `docs/ops/<slug>.md` gồm kiến trúc, các bước, cách kiểm chứng, cách rollback.
3. Viết hoặc sửa theo quy tắc trên.
4. Kiểm chứng ở chế độ an toàn (mục 1) và chạy thật ở local khi được (`docker compose up`, k3d).
5. Báo cáo bàn giao:

```
## Tóm tắt thay đổi
## File đã thay đổi
## Kiểm chứng đã chạy: <lệnh> → kết quả (tóm tắt plan diff nếu có)
## Lệnh cần người dùng xác nhận: môi trường, lệnh, ảnh hưởng, cách hoàn tác
## Rollback
## Rủi ro bảo mật / vận hành / chi phí
```

## 11. Checklist

- [ ] Version được pin (image, action, provider, chart).
- [ ] Không có secret thật trong file commit.
- [ ] Quyền tối thiểu (IAM, client Keycloak, service account).
- [ ] Đã chạy validate / plan / dry-run và đọc kết quả.
- [ ] Có cách rollback; runbook đã cập nhật nếu thêm alert.
