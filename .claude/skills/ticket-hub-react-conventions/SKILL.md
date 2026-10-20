---
name: ticket-hub-react-conventions
description: "Quy tắc frontend React của Ticket Hub: cấu trúc feature, gọi API qua client Orval, TanStack Query / Router, Zustand, form React Hook Form + Zod, đăng nhập qua BFF, hiển thị tiền và giờ, xử lý lỗi Problem Details, sơ đồ ghế react-konva, SSE, shadcn / Tailwind, giao diện theo prototype `docs/design` (khung, toolbar, bộ lọc, dropdown, toast, modal). Dùng khi viết, sửa hoặc review code trong frontend/."
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
├── components/     # component dùng chung không thuộc feature nào (shell/, data-table/, filters/, combobox …)
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
- Thông báo ngắn dùng `sonner` theo kiểu ở `docs/design/README.md` §3 (nền `card`, viền, icon trạng thái, góc dưới phải, `aria-live`); lỗi chặn luồng hiển thị ngay trong trang.
- Trạng thái lỗi có "Thử lại" gọi `refetch`; không hiện mã lỗi kỹ thuật cho người dùng (chỉ mã tra cứu `traceId` khi lỗi 5xx).

## 8. Tiền và thời gian

- Hiển thị tiền qua một hàm duy nhất `formatMoney(amountMinor, currency)` dùng `Intl.NumberFormat('vi-VN', …)`; VND không có phần thập phân. Không tự chia / nhân 100.
- Thời gian từ API là UTC; hiển thị theo `Asia/Ho_Chi_Minh` qua helper trong `lib/date.ts` (date-fns).
- Đồng hồ đếm ngược giữ ghế tính từ `expiresAt` của server, không tự đặt 10 phút ở client.

## 9. Màn hình đặc biệt

- **Sơ đồ ghế (react-konva):** một `Layer` tĩnh cho nền / khu, một `Layer` cho ghế; component ghế `memo`; cập nhật trạng thái ghế theo `seatId`, không vẽ lại toàn bộ; giới hạn số ghế chọn theo BR-02. Khu không có hạng vé hiển thị màu xám, không chọn được.
- **SSE:** hook dùng chung trong `lib/sse.ts`; khi kết nối lại thì tải lại snapshot (`GET /events/{id}/seats` hoặc `GET /queue/{eventId}/status`) rồi nghe tiếp (`overview.md` §7.1, §12).
- **Checkout:** Stripe Payment Element; hiển thị phí nền tảng "không hoàn lại" (BR-07); nút "Huỷ và chọn lại ghế" (BR-12); khoá thanh toán khi còn dưới 90 giây (BR-03).
- **Phòng chờ:** gọi `GET /me` mỗi 5 phút để giữ phiên.
- **Editor sơ đồ ghế (Admin):** theo ADR 0030. Dùng chung component sơ đồ với màn chọn ghế (chế độ sửa / xem) và **một** hàm thuần `generateSeats(shape)` có unit test; kéo khu / phần tử, kéo nền để pan, xoay bước 15°, bắt lưới, undo / redo; sơ đồ `locked` chỉ xem, có "Tạo bản sao để sửa".
- **Thông báo trong app:** chuông ở khung của cả 3 vai trò; số chưa đọc qua `GET /me/notifications/unread-count` (polling 60 giây) cho tới khi có SSE `/me/notifications/stream` (`overview.md` §12).

## 10. UI

**Nguồn thiết kế:** `docs/design/README.md` (token, quy ước §3, danh sách màn §4) và prototype `docs/design/prototype/*.dc.html` (mở trên canvas, bấm Play để xem hành vi; ghi chú "HÀNH ĐỘNG" dưới mỗi màn liệt kê mọi nút). Code đúng bố cục, chữ và trạng thái của màn tương ứng; lệch thì hỏi người dùng, không tự sáng tác.

**Component dùng chung** (làm một lần, mọi màn dùng lại, không viết lại theo từng màn):
- Khung Admin / Organizer: sidebar nhóm, topbar sticky có breadcrumb luôn bắt đầu `Trang chủ ›`, Tìm nhanh (`Command`, Ctrl K), đổi sáng / tối, chuông (popover), menu tài khoản.
- Toolbar danh sách: ghim dưới topbar khi cuộn; trái = tìm + bộ lọc + "Đặt lại" (xuống dòng khi nhiều); phải = Cột · Xuất · menu Excel · nút Tạo (luôn ở hàng đầu). Chọn dòng không thêm hàng mới: nút hàng loạt hiện ở nhóm phải kèm số đã chọn, "Xuất" thành "Xuất (n)".
- Bộ lọc facet (phương án A): nút viền đứt rộng cố định đủ nhãn + 2 giá trị; popover chọn nhiều (dòng "Chọn nhiều" = chọn / bỏ tất cả) hoặc chọn một (radio, "Tuỳ chọn khoảng ngày"); không đếm số lượng theo option; ghi lên URL.
- Dropdown: không dùng `<select>` của trình duyệt; `Select`, danh sách thực thể hoặc trên 7 option dùng combobox có ô tìm ghim đầu, tự mở lên trên khi gần mép dưới.
- Modal / sheet / popover / dropdown: click ra ngoài, nút ✕ và Esc đều đóng, trả focus về nút mở. Chi tiết bản ghi mở side sheet có query trên URL.
- Header bảng nền `secondary`, chữ đậm 600; tiền và giờ không xuống dòng; đủ trạng thái loading / empty / không có kết quả / lỗi.

**Chữ trên giao diện:** chỉ chữ người dùng thật cần đọc. Không hiển thị mã BR, tên bảng / cột, tên event / API (`charges_enabled`, `PaymentIntent`…), câu hướng dẫn kiểu "Hãy…", "Vui lòng…", hay mô tả cơ chế ("ghi audit log", "cập nhật qua webhook"). Thông tin đó thuộc tài liệu.

- Dùng component shadcn trong `components/ui`; cần biến thể riêng thì bọc lại, không sửa sâu file sinh ra.
- Màu, khoảng cách theo token Tailwind của dự án, không hard-code mã màu.
- Văn bản hiển thị qua `react-i18next` (`vi` mặc định), không viết cứng chuỗi trong component.
- Accessibility và checklist trước bàn giao theo skill `ui-ux`.

## 11. Quy trình làm task frontend

1. Đọc hạng mục trong plan, đúng các § overview được nêu, **màn prototype tương ứng** (số màn ghi trong plan, `docs/design/README.md` §4) và `docs/design/<slug>.md` nếu có, rồi đọc code xung quanh (component dùng chung, store, cách gọi API).
2. Chỗ mơ hồ ảnh hưởng tới UX hoặc API: dừng lại hỏi người dùng. Màn hình chưa có trong prototype thì làm bước "Thiết kế màn hình" của skill `ui-ux` trước; màn đã có thì chỉ bổ sung phần prototype chưa thể hiện (luồng lỗi, API, trạng thái).
3. Backend chưa xong: mock bằng MSW theo đúng OpenAPI / plan, không tự thêm field.
4. Làm từng bước nhỏ; viết test cùng lúc theo `ticket-hub-testcase-rule`; sửa bug theo `systematic-debugging`.
5. Chạy lint, type-check, test, build tới khi xanh. Không skip hay tắt test.
6. Kiểm trên trình duyệt thật (Playwright MCP nếu có): các breakpoint, chỉ dùng bàn phím, các trạng thái loading / empty / error; **so với prototype** (bố cục, chữ, hành vi từng nút theo ghi chú "HÀNH ĐỘNG"); chạy `pre-delivery-checklist` của `ui-ux`.
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
