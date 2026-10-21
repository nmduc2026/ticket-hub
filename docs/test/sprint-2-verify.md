# Báo cáo verify Sprint 2

Nhánh `feature/sprint-2`. Chạy ngày 21/10/2026 trên máy dev (Windows 11) **không bật Docker** theo yêu cầu, nên mọi thứ cần Testcontainers hoặc stack thật (Postgres, Keycloak, Gateway) **chưa được chạy lại** trong phiên này. Plan: `docs/plans/2026-10-19-sprint-2-plan.md`, hạng mục 16. Phần còn lại cần chạy khi bật Docker nằm ở cuối file.

## Lệnh đã chạy (không cần Docker)

| Lệnh | Kết quả |
|---|---|
| `cd frontend && npx tsc -b` | Không lỗi |
| `cd frontend && npx oxlint` | Không cảnh báo |
| `cd frontend && npx vitest run` | 16 file, 89 test xanh |
| `cd frontend && npx playwright test mock` (Vite + route mock, 2 khổ 1280px và 390px) | 13 xanh, 1 bỏ qua có chủ đích (click ghế trên canvas ở 390px: ghế quá nhỏ, đã có test bàn phím) |
| `cd backend && ./mvnw -o -pl app test` chọn lọc: `ArchitectureTests`, `ModularityTests`, `BookingStateMachineTests`, `EventStateMachineTests`, `ImageTypeTests`, `Uuid7Tests` | 27 test xanh |
| `cd backend && ./mvnw -o -pl app compile` sau khi sửa chú thích OpenAPI | Biên dịch được |
| `npm run api:generate` sau khi sửa `openapi.json` | Client chỉ đổi ở `booking-controller` và `organizer-event-controller` |
| `terraform init -backend=false && validate && fmt -check` cho `infra/terraform/aws/lab1` | Hợp lệ; render thử `user_data` bằng `terraform console` đúng cú pháp |
| gitleaks (pre-commit) mỗi commit | Không có secret |

## Truy vết AC

"BE chạy xanh 21/10" là kết quả `./mvnw verify` trên Docker ngày 21/10/2026 sau khi refactor cấu trúc package (xem mục "Verify trên Docker").

| AC | Bằng chứng | Kết quả |
|---|---|---|
| AC-1 (BR-05, BR-14) Tạo → hạng vé → gán khu → publish; sau publish chỉ sửa được thông tin hiển thị | BE: `EventPublishApiTests`, `OrganizerEventApiTests` (`afterPublishOnlyDisplayInfoCanChange`, `afterPublishTiersAndAssignmentsAreLocked`) đã viết, chưa chạy. FE: `eventEdit.test.tsx` (sau publish giờ / địa điểm / sơ đồ khoá, tiêu đề lưu được và gửi nguyên các trường khoá; `EVENT_LOCKED` hiện trong trang), `e2e/organizer-publish.mock.spec.ts` (cả luồng tạo → publish, 2 khổ màn hình) | FE đạt trên mock; BE chờ chạy |
| AC-2 (BR-06) `platform_fee_bps` = mức hiện hành | `EventPublishApiTests.feeIsFixedAtPublish…` đã viết. FE hiển thị "5% giá vé" từ `platformFeeBps` sau publish | Đạt (BE chạy xanh 21/10) |
| AC-3 (§7.9) Sau publish có tồn kho đúng số ghế, `inventory_ready`, tới giờ thì `ON_SALE`; chưa sẵn sàng thì không `ON_SALE` | `BookingProjectionTests`, `EventPublishApiTests.eventStartsSellingOnlyWhenTheSaleHasStartedAndInventoryIsReady` đã viết | Đạt (BE chạy xanh 21/10) |
| AC-4 Organizer khác 404, customer gọi `/organizer/**` 403, chưa đăng nhập 401 | BE: `OrganizerEventApiTests` (3 test tương ứng) đã viết. FE: `eventEdit.test.tsx` "404 chỉ hiện Không tìm thấy sự kiện", `routing.test.tsx` chặn route theo role | Đạt (BE chạy xanh 21/10) |
| AC-5 Guest xem danh sách, tìm, chi tiết, sơ đồ có trạng thái; khu không bán màu xám, không chọn được | BE: `PublicEventApiTests`. FE: `SeatMap.test.tsx` (ghế đã bán / giữ / không bán / khu không gán hạng vé không chọn được), `e2e/seat-hold.mock.spec.ts` (click trên canvas thật: khu C xám không chọn được; ảnh chụp 1280px đúng bố cục #5) | Đạt (BE chạy xanh 21/10) |
| AC-6 (BR-01, BR-02) Mã lỗi giữ ghế | BE: `BookingHoldTests` (201 + `expiresAt`, `SEAT_UNAVAILABLE`, 6 / 7 ghế, `PENDING_BOOKING_EXISTS`, `TICKET_LIMIT_EXCEEDED` biên, `EVENT_NOT_ON_SALE`) đã viết. FE: `bookingFlow.test.tsx` (map từng mã sang tiếng Việt, hiện trong trang, giữ / bỏ lựa chọn đúng) | Đạt (BE chạy xanh 21/10) |
| AC-7 (§6.7) Cùng key → cùng response; cùng key khác body → 422 | BE: `BookingHoldTests` (3 test) đã viết. FE: `bookingFlow.test.tsx` (key sinh mỗi lần bấm, **giữ nguyên khi retry 503**, **đổi khi lần bấm mới sau 409**) | Đạt (BE chạy xanh 21/10) |
| AC-8 (BR-01) 50 luồng cùng giữ 1 ghế → đúng 1 thành công | `BookingHoldTests.fiftyConcurrentRequestsForOneSeatYieldExactlyOneWinner` và `overlappingSeatSetsInOppositeOrder…` đã viết | Đạt (BE chạy xanh 21/10; kèm truy vấn ghế trùng = 0 trong test) |
| AC-9 (BR-03) Quá 10 phút → `EXPIRED`, ghế `AVAILABLE` | `BookingExpiryAndCancelTests` (dùng `Clock` cố định) đã viết. FE: màn giữ ghế hiện "Hết thời gian giữ ghế" khi `EXPIRED` hoặc đồng hồ về 0 (`bookingFlow.test.tsx`) | Đạt (BE chạy xanh 21/10) |
| AC-10 (BR-12) Huỷ → `CANCELLED`, ghế trống ngay; người khác 404; đã hết hạn 409 | BE: `BookingExpiryAndCancelTests` đã viết. FE: huỷ rồi quay về chọn ghế, `BOOKING_NOT_CANCELLABLE` hiện trong trang và tải lại đơn; Playwright mock: huỷ → chọn lại đúng ghế đó | Đạt (BE chạy xanh 21/10) |
| AC-11 Lab AWS: mở frontend qua CloudFront, đăng nhập được; sau destroy `verify-clean.sh` sạch | `infra/terraform/aws/lab1/` đã viết, `terraform validate` đạt | **Chưa đạt**: chưa `plan` / `apply` (cần tài khoản AWS và xác nhận từng lần) |
| AC-12 Danh sách sự kiện organizer: lọc, từ khoá lên URL, tải lại giữ nguyên, toolbar ghim, "Xuất (n)", khớp #12 | `OrganizerEventsPage.test.tsx`, `FacetFilter.test.tsx`; `e2e/organizer-events.mock.spec.ts` (tải lại giữ bộ lọc và từ khoá, toolbar ghim ngay dưới topbar khi cuộn, "Xuất (2)") | Đạt (trên mock) |

## Đo hiệu năng sơ đồ ~500 ghế

`e2e/seat-hold.mock.spec.ts` phóng / thu liên tục 20 bước: trung bình 35–40 ms mỗi bước (gồm một khung hình chờ), build dev, chưa tối ưu. Đạt ngưỡng 50 ms đặt trong test. Chưa đo với ~20.000 ghế (ADR 0030, Sprint 6).

## Thay đổi hợp đồng API trong phiên này

- `GET /bookings/{id}`, `GET / PUT /organizer/events/{id}`, `POST …/publish`, `POST …/banner`: khai thêm response 200; body lỗi 404 / 409 / 422 khai là Problem Details (trước đó springdoc gán nhầm body thành công cho response lỗi). `openapi.json` sửa tay theo đúng định dạng springdoc; **`OpenApiContractTests` cần chạy lại khi bật Docker** để chắc file khớp.

## Verify trên Docker (21/10/2026, sau refactor cấu trúc package)

| Lệnh | Kết quả |
|---|---|
| `cd backend && ./mvnw verify -Dspotless.check.skip=true` (Testcontainers: Postgres, Keycloak) | BUILD SUCCESS cho `app` và `gateway`: 28 lớp test, gồm `BookingHoldTests` (18, có 50 luồng tranh 1 ghế và truy vấn ghế trùng), `BookingExpiryAndCancelTests` (12), `EventPublishApiTests` (13), `OrganizerEventApiTests` (20), `OpenApiContractTests` (2), `MeEndpointTests` (7), `BffLoginFlowTests` (8) |
| compose `core` + `app` :8081 + `gateway` :8080 + `npm run dev` :5173, `cd frontend && npx playwright test` | 36 xanh, 4 bỏ qua có chủ đích (test chỉ áp dụng cho một khổ màn hình); `app` khởi động trên Postgres thật, Flyway chạy cả migration `payment` |
| `OpenApiContractTests` ghi lại `openapi.json` | Khác bản sửa tay: thêm schema `ProblemDetail`, và `JsonNode` được suy ra khác nhau giữa các lần chạy. Đã khai `JsonNode` là object tự do (`OpenApiConfiguration`), chạy 2 lần cho cùng kết quả, commit `openapi.json` mới và sinh lại client Orval; `tsc -b` sạch, `vitest` 89 test xanh |

Chưa làm: hạng mục 17 (review cuối sprint kèm subagent review SQL giữ ghế) và AC-11 (lab AWS).

## Ghi chú môi trường

- `./mvnw spotless:check` báo lỗi hàng loạt vì file Java checkout ở Windows có CRLF (`\r\n`) trong khi Spotless đòi LF. Có từ trước, không do thay đổi này; CI chạy ở Linux nên không gặp. Nên thêm `.gitattributes` (`* text=auto eol=lf`) cho repo.
