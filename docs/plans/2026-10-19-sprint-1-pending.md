# Việc còn treo: Sprint 1

Ghi những việc chưa xong hoặc cần người dùng làm. Xoá dòng khi xong.

## Cần người dùng

| # | Việc | Vì sao treo | Cách làm |
|---|---|---|---|
| 1 | Push `feature/sprint-1`, mở PR, xem CI chạy lần đầu | Push / PR cần báo trước. Chưa chạy workflow thật nên AC-7 (CI trên PR), "CI xanh trên `main`" và "image có trên GHCR" của việc 8 chưa kiểm chứng | Duyệt rồi ra lệnh push; nếu CI đỏ thì sửa theo log |
| 2 | Cấu hình SonarCloud | Cần tài khoản và token | `docs/ops/ci-cd.md` mục "Cần cấu hình một lần"; chưa có thì bước Sonar tự bỏ qua |
| 3 | Cài GitHub App Renovate | Cần quyền chủ repo | Cài app cho repo; cấu hình nằm ở `renovate.json` |
| 4 | Làm checklist bảo vệ tài khoản AWS | Thao tác trên console AWS | `docs/ops/aws-account-protection.md` (MFA, Identity Center, budget 0,01$) |
| 5 | Chạy Terraform thăm dò từng dịch vụ rồi điền bảng | `terraform apply` luôn hỏi từng lần | `infra/terraform/aws/probe/README.md`; kết quả vào `docs/ops/aws-free-plan-probe.md` (AC-8) |
| 6 | Bảo vệ nhánh `main` | Cài đặt repo | Yêu cầu job `backend`, `frontend`, `e2e`, `secrets` xanh, squash merge |

## Việc kỹ thuật còn lại

| # | Việc | Ghi chú |
|---|---|---|
| 1 | Hồ sơ cá nhân (màn #19) còn thiếu: ảnh đại diện, số điện thoại, ngày sinh, thành phố, tài khoản liên kết, danh sách phiên, xoá tài khoản | `user_profiles` (§6.6) chưa có các cột này và chưa có API. Hiện chỉ hiển thị thông tin từ `GET /me`; đổi email, mật khẩu, phiên dẫn sang trang tài khoản Keycloak. Cần quyết định thêm trường hay để Keycloak giữ |
| 2 | Chạy `app`, `gateway`, `frontend` dạng container trong compose profile `core` | Cần chốt hostname Keycloak (trình duyệt dùng `localhost:8180`, container dùng `keycloak:8080`). Hiện CI và dev chạy `app` / `gateway` bằng `java -jar` / `mvnw` |
| 3 | Orval 7.13 chưa sinh file Zod | Spec mới có `GET`. Kiểm lại khi có endpoint nhận body (Sprint 2); Orval 8 cần Node ≥ 22.18 (máy dev đang Node 20) |
| 4 | Spring Cloud 2025.1.3 build cho Boot 4.0.x, đang chạy trên Boot 4.1.1 | Test Gateway xanh. Nâng khi có release train cho Boot 4.1 |
| 5 | Ghi đè phiên bản `tomcat` 11.0.27 và `jackson-bom` 3.1.8 trong `backend/pom.xml` | Để hết CVE CRITICAL; bỏ khi Spring Boot cập nhật |
| 6 | Máy dev dùng npm 10.8, lỗi khi phân giải peer dependency của Vite 8 | Dùng `npx -y npm@11 install`; Dockerfile và CI đã cài npm 11 |
| 7 | Hợp đồng OpenAPI được ghi ra `frontend/openapi/openapi.json` bởi test backend | CI kiểm không lệch; đổi API thì commit file này |
| 8 | Màn Check-in, ô tìm kiếm, link "Vé của tôi" / "Bán vé cùng Ticket Hub", tab danh mục ở layout khách | Thêm khi các trang đó có (Sprint 2 trở đi) |
