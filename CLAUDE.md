# Ticket Hub

Đọc file này đầu tiên để nạp bối cảnh. Thiết kế chi tiết nằm ở `overview.md`; file này chỉ tóm tắt. Mục nào còn `TODO` là chưa chốt, hỏi người dùng trước khi tự giả định.

## Tổng quan
- Nền tảng bán vé sự kiện / concert có flash sale, dạng marketplace: ban tổ chức bán vé, nền tảng thu phí.
- Dự án cá nhân, mục tiêu học xây dựng **và vận hành** hệ thống mức production. Chi phí ~0$.
- Actor: Guest, Customer, Organizer, Admin (`overview.md` §2).
- Bất biến số 1: **không bao giờ bán 1 ghế cho 2 người** (oversell). Đã trừ tiền thì phải có vé hoặc được hoàn tiền tự động.
- Quy tắc nghiệp vụ có mã BR-xx (`overview.md` §3). Khi viết requirement, test hay code, trích đúng mã BR.
- Trạng thái: mới có bản thiết kế, chưa có code. Lộ trình 9 sprint, hạn 18/12/2026: `docs/plans/2026-10-19-roadmap.md`.
- Giao diện: `docs/design/README.md` (token, quy ước, danh sách màn theo sprint) + prototype `docs/design/prototype/` (canvas: https://claude.ai/artifact/13t7cm5BAqJ63AZa18fDgU). Code FE bám theo màn prototype tương ứng.

## Tech stack
- Backend: Java 21 (virtual threads), Spring Boot, Spring MVC REST, Spring Modulith, Maven
  - Data: PostgreSQL, Spring Data JPA + Hibernate, jOOQ (báo cáo), Flyway
  - Redis (Lettuce, Redisson, Bucket4j), Kafka (KRaft) + Avro + Schema Registry, Debezium (outbox)
  - Auth: Keycloak, Spring Cloud Gateway làm BFF (session cookie, SPA không cầm token)
  - Khác: Elasticsearch, Stripe (test mode, Connect), Resilience4j, ShedLock, Unleash, Vault
- Frontend: React 19, TypeScript strict, Vite, npm
  - shadcn/ui + Tailwind CSS v4, TanStack Router, TanStack Query, Zustand
  - Orval sinh client từ OpenAPI (hooks + Zod), React Hook Form + Zod, react-konva (sơ đồ ghế)
- Test: JUnit, Testcontainers, WireMock, ArchUnit, Pact (BE); Vitest, RTL, MSW (FE); Playwright (E2E); JMeter (load)
- Hạ tầng: Docker Compose (dev), k3d/k3s + Helm, ArgoCD, Prometheus/Grafana, ELK, Jaeger, GitHub Actions, Terraform (AWS lab)

Danh sách đầy đủ: `overview.md` §21.

## Lệnh thường dùng
```bash
# Backend (chạy trong backend/, cần Docker cho Testcontainers):
#   ./mvnw verify                                  # build + toàn bộ test
#   DB_PASSWORD=... ./mvnw -pl app spring-boot:run # app ở :8081, cần compose profile core
# TODO: Frontend (npm) điền khi scaffold frontend/
# Hạ tầng dev (chạy trong infra/docker-compose/, lần đầu: cp .env.example .env):
#   docker compose --profile <core|kafka|search|observability|platform> up -d
# Bật gitleaks pre-commit (một lần sau khi clone): git config core.hooksPath .githooks
```

## Cấu trúc thư mục
Monorepo (`overview.md` §22):
- `backend/`: Maven multi-module ngay từ đầu, `backend/pom.xml` là parent
  - `backend/gateway/`: Spring Cloud Gateway làm BFF, ứng dụng riêng
  - `backend/app/`: modular monolith, package gốc `com.ndz.backend`, mỗi module nghiệp vụ là 1 package: `account`, `catalog`, `booking`, `payment`, `ticket`, `notification`, `shared`; `queue` (phòng chờ) thêm ở Sprint 7
  - Giai đoạn đầu event giữa các module đi bằng Spring Modulith in-process; Kafka vào ở Sprint 8
- `frontend/`: tổ chức theo feature (`src/features/<feature>`); `src/api/generated/` do Orval sinh, không sửa tay
- `infra/`: docker-compose, helm, argocd, keycloak, terraform
- `load-tests/jmeter/`
- `docs/`: `plans/` (lộ trình, plan sprint có AC), `adr/`, `briefs/`, `design/`, `test/`, `ops/`, `runbooks/`, `postmortems/`

## Convention
- Tên dự án: "Ticket Hub" khi hiển thị; định danh kỹ thuật (Keycloak realm/client, audience, tên image...) dùng `tickethub`. Tên cũ "TicketRush" không dùng nữa
- API: REST/JSON, tiền tố `/api/v1`, lỗi theo RFC 9457 Problem Details kèm `code` nghiệp vụ (vd. `SEAT_UNAVAILABLE`), path danh từ số nhiều kebab-case
- Contract: OpenAPI sinh bằng springdoc là nguồn sự thật cho FE
- `Idempotency-Key` bắt buộc với `POST /bookings`, `POST /payments`, `POST /bookings/{id}/refund`
- Tiền: `amount_minor` BIGINT + mã tiền tệ, mặc định VND (zero-decimal, không nhân 100). Không dùng float/double
- Thời gian: lưu UTC (`timestamptz`), hiển thị `Asia/Ho_Chi_Minh`
- Khoá chính UUIDv7; bảng có cập nhật đồng thời dùng cột `version`
- Module không truy cập dữ liệu (bảng, class nội bộ) của module khác. Giao tiếp qua event (outbox), hoặc gọi interface công khai của module khi người dùng đang chờ kết quả (vd. Payment hỏi Booking lúc tạo thanh toán). Consumer phải idempotent
- Tài liệu thiết kế (`overview.md`): viết như bản thiết kế hoàn chỉnh, ở thì hiện tại. Không ghi lịch sử quyết định, ngày chốt, mã review hay "đã sửa so với bản cũ"; những thứ đó ghi ở `docs/plans/*-review.md` hoặc ADR. Khi thêm một quyết định, sửa thẳng BR/mục liên quan thay vì thêm dòng "quyết định"
- Git:
  - `main` luôn build và deploy được. Nhánh: `feat/<slug>`, `fix/<slug>`, `infra/<slug>`, `docs/<slug>`, `chore/<slug>`.
  - Commit theo Conventional Commits, scope là module: `feat(booking): giữ ghế bằng Postgres (BR-02, BR-03)`. Ghi mã BR khi liên quan.
  - 1 PR = 1 việc kiểm chứng được. Mô tả PR: mục tiêu, BR / AC liên quan, cách test, kết quả verify.  - Chỉ merge khi CI xanh; squash merge vào `main`.

## Môi trường & deploy
- dev: máy 16GB, Docker Compose theo profile
- staging: máy 32GB, k3d
- aws-lab: AWS `ap-southeast-1`, tạo bằng Terraform và destroy ngay sau lab
- Thao tác cần **báo trước** cho người dùng: commit, push, mở PR, merge, deploy (k3d hay AWS), `kubectl`/`helm` lên cluster, `terraform apply`/`destroy`, chạy migration trên database không phải local/test.
  - Agent được thực hiện các thao tác này, nhưng trước khi làm phải báo: làm gì, trên môi trường nào, lệnh cụ thể, ảnh hưởng và cách hoàn tác. Chờ người dùng đồng ý rồi mới làm.
  - Người dùng trả lời **"skip"** cho một thao tác, hoặc cho cả một loại thao tác (vd. "skip commit"), thì agent tự làm mà không cần hỏi lại, nhưng vẫn ghi lại trong báo cáo.
  - Subagent không hỏi trực tiếp được người dùng: dừng ở bước chuẩn bị, ghi thao tác cần làm vào báo cáo; session chính hỏi người dùng rồi mới giao làm tiếp.
  - Luôn hỏi từng lần, không áp dụng "skip": `terraform apply` tạo tài nguyên AWS tốn tiền.
- Không bao giờ làm: dùng Stripe live mode hoặc dữ liệu khách hàng thật; commit secret (Stripe key, Keycloak client secret...).

## Cách làm việc
- Không dùng team agent. Session chính làm trực tiếp, nạp skill theo vùng việc (bảng dưới). Người dùng là PM: đưa yêu cầu, duyệt plan, review.
- **Một session cho một hạng mục** trong plan sprint; xong hạng mục thì báo cáo bàn giao, người dùng `/clear` trước hạng mục kế tiếp.
- **Không đọc cả `overview.md`** (rất dài): chỉ đọc các § mà plan / hạng mục chỉ ra, tìm theo tiêu đề.
- Yêu cầu mơ hồ → `brainstorm`. Bắt đầu sprint / việc lớn, viết ADR, review cuối sprint → `tech-lead`.
- Review độc lập (tuỳ chọn, vùng rủi ro cao: tiền, oversell, bảo mật): gọi subagent general-purpose review diff theo skill `code-review`.

## Skill
| Vùng việc | Skill nạp |
|---|---|
| Mọi code | `ticket-hub-coding-conventions` |
| Backend (`backend/`) | `ticket-hub-springbot-conventions` (gồm quy trình và mẫu báo cáo) |
| Frontend (`frontend/`) | `ticket-hub-react-conventions` (gồm quy trình, component dùng chung theo prototype và mẫu báo cáo); màn chưa có trong prototype: `ui-ux` |
| Hạ tầng (`infra/`, `.github/`, Dockerfile, AWS) | `ticket-hub-infra-conventions` |
| Test | `ticket-hub-testcase-rule` |
| Plan, ADR, review cuối sprint | `tech-lead` |
| Trước khi báo xong | `verification`; tự review theo phần B của `code-review` |
| Bug | `systematic-debugging` |
| Chọn phương án kỹ thuật | `solution-options` |
