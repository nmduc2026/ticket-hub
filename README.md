# Ticket Hub

Nền tảng bán vé sự kiện / concert có flash sale, dạng marketplace: ban tổ chức bán vé, nền tảng thu phí. Hỗ trợ chọn ghế theo sơ đồ, phòng chờ ảo, thanh toán Stripe (test mode), vé QR có chữ ký số và check-in tại cổng.

Thiết kế hệ thống: [overview.md](overview.md).

## Tech stack

| Phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot, Spring Modulith, Maven, PostgreSQL, Redis, Kafka, Keycloak |
| Frontend | React 19, TypeScript, Vite, shadcn/ui, Tailwind CSS v4, TanStack Router/Query |
| Hạ tầng | Docker Compose, Kubernetes (k3d/k3s), Helm, ArgoCD, Prometheus/Grafana, ELK, Jaeger |

## Bắt đầu

TODO: yêu cầu môi trường, cài đặt, chạy local (điền khi scaffold `backend/` và `frontend/`).

## Làm việc với Claude Code

Bối cảnh dự án ở `CLAUDE.md`; skill trong `.claude/skills/` (quy tắc theo vùng backend / frontend / hạ tầng / test, cùng `tech-lead`, `brainstorm`, `code-review`, `verification`…). Không dùng team agent: mỗi session làm một hạng mục trong plan sprint.

```text
# Lập plan cho sprint
/tech-lead Lập plan Sprint 2 theo docs/plans/2026-10-19-roadmap.md

# Làm một hạng mục
Làm hạng mục 3 trong docs/plans/2026-10-19-sprint-1-plan.md
```
