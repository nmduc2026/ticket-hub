# Việc còn treo: Sprint 2

Ghi những việc chưa xong hoặc cần người dùng làm. Xoá dòng khi xong.

## Cần người dùng

| # | Việc | Vì sao treo | Cách làm |
|---|---|---|---|
| 1 | Bật Docker rồi chạy `./mvnw verify` và Playwright trên stack thật | Phiên làm FE không bật Docker; AC-1…10 phía backend (kể cả 50 luồng tranh 1 ghế) chưa chạy lại | Xem cuối `docs/test/sprint-2-verify.md` |
| 2 | Duyệt `docs/design/seat-map.md` | Việc 10 yêu cầu người dùng duyệt phần bổ sung (luồng lỗi, polling, bàn phím) | Đọc và báo chỗ cần đổi |
| 3 | Chạy lab AWS 1 | `terraform apply` luôn hỏi từng lần; cần tài khoản AWS, checklist bảo vệ và bảng thăm dò Free plan (việc treo Sprint 1 số 4, 5) | `infra/terraform/aws/lab1/README.md`; xong thì `destroy` và `verify-clean.sh` |
| 4 | Push `feature/sprint-2`, mở PR | Push / PR cần báo trước | Duyệt rồi ra lệnh |
| 5 | Hoàn tất hạng mục 16 và làm hạng mục 17 | Hạng mục 16 mới verify phần FE và test backend không cần Docker; hạng mục 17 (review cuối sprint theo skill `tech-lead`, kèm subagent review SQL giữ ghế) chưa làm vì cần verify backend xong trước | Sau mục 1: cập nhật `docs/test/sprint-2-verify.md`, rồi gọi review |
| 6 | Chạy lại `OpenApiContractTests` | `openapi.json` được sửa tay (response 200 và Problem Details cho booking và organizer) nên chưa chắc khớp bản springdoc sinh ra | Khi bật Docker: nếu test ghi file khác thì commit bản mới rồi `npm run api:generate` |

## Việc kỹ thuật còn lại

| # | Việc | Ghi chú |
|---|---|---|
| 1 | `Idempotency-Key` chưa có trong OpenAPI của `POST /bookings` | Orval không sinh tham số header nên FE gọi hàm `createBooking(data, { headers })` trực tiếp trong `useHoldSeats`. Khai header bằng `@Parameter` ở backend rồi sinh lại |
| 2 | `shape` của khu / phần tử sơ đồ ghế bị sinh thành `JsonNode` rỗng | FE đọc bằng schema Zod trong `seatGeometry.ts`. Nên khai `shape` là object ở OpenAPI |
| 3 | `PENDING_BOOKING_EXISTS` chưa kèm `bookingId` | Khách không có link quay lại đơn đang giữ (chưa có `GET /me/bookings`, Sprint 3). Đề xuất thêm `bookingId` vào Problem Details |
| 4 | Bộ lọc facet chưa có "Tuỳ chọn khoảng ngày" | README §3; danh sách sự kiện organizer chưa cần lọc theo ngày. Thêm khi có màn cần |
| 5 | Breadcrumb khung Admin / Organizer chỉ 2 cấp | Prototype #13, #24 có `Trang chủ › Sự kiện › <tên> › <tab>`; trang sửa tạm hiện tên và tab ở đầu trang |
| 6 | Trang sửa sự kiện chưa có: thẻ, độ tuổi, quy định, giờ mở cửa, hiển thị công khai / chỉ link, kết thúc bán, xem trước | Không có trường tương ứng ở API; không tự thêm field |
| 7 | Tab "Phòng chờ" ở trang sửa sự kiện | Sprint 7 |
| 8 | Link "Kết nối Stripe" trong hộp thoại "Chưa thể publish" | Sprint 6 (Stripe Connect); hiện chỉ có nút "Đã hiểu" |
| 9 | Upload banner trên lab AWS | App còn trỏ S3 giả; cần bucket media thật hoặc S3Mock trên EC2 |
| 10 | E2E trên stack thật cho luồng giữ ghế và publish | Hiện chỉ có spec `*.mock.spec.ts` chạy với `npm run dev` |
| 11 | Thêm `.gitattributes` (`* text=auto eol=lf`) | Spotless báo CRLF trên Windows nên `spotless:check` đỏ ở máy dev, dù CI Linux không gặp |
| 12 | Sơ đồ ghế chưa vẽ khu IMPORTED và khu xoay | `seatGeometry.sectionRect` bỏ qua `rotation`; khu IMPORTED không có khung. Làm cùng editor (Sprint 6, ADR 0030) |
| 13 | Click ghế trên canvas ở 390px | Ghế ~6 px; cần zoom trước khi chọn. Cân nhắc phóng sẵn hoặc vùng chạm lớn hơn |
