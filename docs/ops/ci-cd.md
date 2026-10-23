# CI/CD (Sprint 1)

Workflow: `.github/workflows/ci.yml`. Chạy trên mọi PR và mọi push vào `main` (`overview.md` §19.3).

## Các job

| Job | Làm gì | Gate |
|---|---|---|
| `secrets` | gitleaks quét toàn bộ lịch sử | Có secret → đỏ |
| `backend` | `./mvnw verify`: Error Prone khi biên dịch, test (gồm Testcontainers), Spotless, JaCoCo; kiểm `frontend/openapi/openapi.json` không lệch | Test / Spotless / Error Prone lỗi → đỏ |
| `frontend` | lint (oxlint), type-check, Vitest, build; kiểm client Orval sinh ra không lệch | Lỗi → đỏ |
| `e2e` | Dựng Postgres + Redis + Keycloak (compose `core`), chạy `app`, `gateway`, Vite dev rồi Playwright: đăng nhập 3 role, chặn role, đăng xuất, khung giao diện ở 1280px và 390px | Lỗi → đỏ |
| `images` | Build image `app`, `gateway`, `frontend`; Trivy; đẩy GHCR **chỉ khi push vào `main`** | CRITICAL đã có bản vá → đỏ; HIGH chỉ báo |

Image: `ghcr.io/<chủ repo>-{app,gateway,frontend}:sha-<commit>` và `:main`. Không dùng `latest`.

## Cần cấu hình một lần (người dùng làm)

| Việc | Cách |
|---|---|
| Gói GHCR | Lần đầu đẩy xong, vào Packages đặt quyền cho repo; để private nếu không muốn công khai |
| Bảo vệ nhánh | Settings → Branches: `main` yêu cầu job `backend`, `frontend`, `e2e`, `secrets` xanh, squash merge |

## Chạy lại cục bộ

```bash
cd backend && ./mvnw verify                      # backend
cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run build
docker build -f backend/Dockerfile --build-arg MODULE=app -t tickethub/app:local backend
docker build -f backend/Dockerfile --build-arg MODULE=gateway -t tickethub/gateway:local backend
docker build -t tickethub/frontend:local frontend
```

Spotless tự sửa: `./mvnw spotless:apply`.

## Rollback

Workflow chỉ đọc mã và đẩy image; không động tới môi trường chạy. Muốn bỏ một image lỗi: xoá tag trong GHCR. Quay lại workflow cũ: revert commit sửa `ci.yml`.
