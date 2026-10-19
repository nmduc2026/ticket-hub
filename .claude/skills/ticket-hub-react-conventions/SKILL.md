---
name: ticket-hub-react-conventions
description: "Quy tắc frontend React của Ticket Hub: cấu trúc feature, gọi API qua client Orval, TanStack Query / Router, Zustand, form React Hook Form + Zod, đăng nhập qua BFF, hiển thị tiền và giờ, xử lý lỗi Problem Details, sơ đồ ghế react-konva, SSE, shadcn / Tailwind. Dùng khi viết, sửa hoặc review code trong frontend/."
---

# Ticket Hub: quy tắc React

Đọc kèm `ticket-hub-coding-conventions`; giao diện và a11y theo skill `ui-ux`. Stack: React 19, TypeScript strict, Vite, shadcn/ui + Tailwind v4, TanStack Router / Query, Zustand, Orval, React Hook Form + Zod, react-konva (`overview.md` §14, §21.2).

## 1. Cấu trúc

```
src/
├── app/            # providers, router, query client
├── routes/         # route file-based (TanStack Router), chỉ ghép layout + feature
├── features/<x>/   # components/, hooks/, stores/, utils/ của riêng feature
├── components/ui/  # shadcn (sinh ra, chỉnh tối thiểu)
├── components/     # component dùng chung không thuộc feature nào
├── api/generated/  # Orval sinh ra — KHÔNG sửa tay
└── lib/            # api client, sse, money, date, error mapping
```

- Feature không import file nội bộ của feature khác.
- File component `PascalCase.tsx`; hook `useXxx.ts`; file khác `kebab-case.ts`.
- Một component một file; component quá ~150 dòng thì tách.

## 2. TypeScript

- `strict` bật; không dùng `any`; không dùng `!` (non-null assertion) trừ khi có comment giải thích.
- Type dữ liệu API lấy từ code Orval sinh ra, không tự khai báo lại.
- Ưu tiên union type cho trạng thái (`'AVAILABLE' | 'HELD' | 'SOLD'`) thay vì string tự do.

## 3. Gọi API

- **Chỉ gọi API qua hook / hàm Orval sinh ra.** Không dùng `fetch` / `axios` trực tiếp trong component.
- API client dùng chung (`lib/api-client.ts`) gửi kèm `credentials: 'include'`, header `X-Requested-With: XMLHttpRequest` và `X-XSRF-TOKEN` lấy từ cookie (`overview.md` §13.1).
- Nhận `401` → chuyển trình duyệt tới `/oauth2/authorization/keycloak` (lưu trang hiện tại để quay lại). Nhận `403` → trang "không có quyền".
- API ghi bắt buộc idempotency (`POST /bookings`, `/payments`, `/bookings/{id}/refund`): sinh `Idempotency-Key` **một lần cho mỗi lần người dùng bấm**, giữ nguyên khi retry.

## 4. State

- **Dữ liệu từ server: TanStack Query.** Không chép dữ liệu server vào Zustand hay `useState`.
- **State giao diện / client: Zustand**, store nhỏ theo feature (vd. ghế đang chọn). Không tạo một store toàn cục chứa mọi thứ.
- Query key theo Orval; sau khi ghi thì `invalidateQueries` đúng key, không refetch tràn lan.
- Không lưu token hay dữ liệu nhạy cảm vào `localStorage`.

## 5. Routing và phân quyền

- Route file-based; bảo vệ route bằng `beforeLoad` đọc `GET /me` (cache qua Query).
- Ẩn nút theo role chỉ là UX; quyền thật do backend kiểm.

## 6. Form

- React Hook Form + Zod. Schema Zod lấy từ Orval khi có; quy tắc riêng của form mở rộng từ schema đó.
- Lỗi từ server (Problem Details có `errors` theo field) map vào field tương ứng.
- Nút submit bị khoá khi đang gửi; không cho bấm 2 lần.

## 7. Lỗi, loading, trống

- Mỗi màn hình có đủ 3 trạng thái: đang tải (skeleton), lỗi (có nút thử lại), trống.
- Map `code` của Problem Details sang thông báo tiếng Việt ở một chỗ (`lib/error-messages.ts`), vd. `SEAT_UNAVAILABLE` → "Ghế vừa có người giữ, vui lòng chọn ghế khác".
- Thông báo ngắn dùng `sonner`; lỗi chặn luồng hiển thị ngay trong trang.

## 8. Tiền và thời gian

- Hiển thị tiền qua một hàm duy nhất `formatMoney(amountMinor, currency)` dùng `Intl.NumberFormat('vi-VN', …)`; VND không có phần thập phân. Không tự chia / nhân 100.
- Thời gian từ API là UTC; hiển thị theo `Asia/Ho_Chi_Minh` qua helper trong `lib/date.ts` (date-fns).
- Đồng hồ đếm ngược giữ ghế tính từ `expiresAt` của server, không tự đặt 10 phút ở client.

## 9. Màn hình đặc biệt

- **Sơ đồ ghế (react-konva):** một `Layer` tĩnh cho nền / khu, một `Layer` cho ghế; component ghế `memo`; cập nhật trạng thái ghế theo `seatId`, không vẽ lại toàn bộ; giới hạn số ghế chọn theo BR-02. Khu không có hạng vé hiển thị màu xám, không chọn được.
- **SSE:** hook dùng chung trong `lib/sse.ts`; khi kết nối lại thì tải lại snapshot (`GET /events/{id}/seats` hoặc `GET /queue/{eventId}/status`) rồi nghe tiếp (`overview.md` §7.1, §12).
- **Checkout:** Stripe Payment Element; hiển thị phí nền tảng "không hoàn lại" (BR-07); nút "Huỷ và chọn lại ghế" (BR-12); khoá thanh toán khi còn dưới 90 giây (BR-03).
- **Phòng chờ:** gọi `GET /me` mỗi 5 phút để giữ phiên.

## 10. UI

- Dùng component shadcn trong `components/ui`; cần biến thể riêng thì bọc lại, không sửa sâu file sinh ra.
- Màu, khoảng cách theo token Tailwind của dự án, không hard-code mã màu.
- Văn bản hiển thị qua `react-i18next` (`vi` mặc định), không viết cứng chuỗi trong component.
- Accessibility và checklist trước bàn giao theo skill `ui-ux`.

## 11. Quy trình làm task frontend

1. Đọc hạng mục trong plan, đúng các § overview được nêu, thiết kế `docs/design/<slug>.md` nếu có, rồi đọc code xung quanh (component, store, cách gọi API).
2. Chỗ mơ hồ ảnh hưởng tới UX hoặc API: dừng lại hỏi người dùng. Màn hình mới phức tạp chưa có thiết kế thì làm bước "Thiết kế màn hình" của skill `ui-ux` trước.
3. Backend chưa xong: mock bằng MSW theo đúng OpenAPI / plan, không tự thêm field.
4. Làm từng bước nhỏ; viết test cùng lúc theo `ticket-hub-testcase-rule`; sửa bug theo `systematic-debugging`.
5. Chạy lint, type-check, test, build tới khi xanh. Không skip hay tắt test.
6. Kiểm trên trình duyệt thật (Playwright MCP nếu có): các breakpoint, chỉ dùng bàn phím, các trạng thái loading / empty / error; chạy `pre-delivery-checklist` của `ui-ux`.
7. Tự review diff theo phần B của `code-review`; lập bảng truy vết theo `verification`.
8. Báo cáo bàn giao:

```
## Tóm tắt thay đổi
## File đã thay đổi
## Bảng truy vết AC → bằng chứng → kết quả
## Lệnh đã chạy → kết quả; breakpoint / bàn phím đã kiểm
## Pre-delivery checklist: mục chưa đạt (ID rule + lý do)
## Phụ thuộc backend / đề xuất đổi API
## Rủi ro, việc còn lại
```
