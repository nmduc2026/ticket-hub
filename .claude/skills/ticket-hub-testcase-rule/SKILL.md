---
name: ticket-hub-testcase-rule
description: "Quy tắc viết test case và test tự động của Ticket Hub: map test vào BR / AC, chọn tầng test (unit, kiến trúc, integration Testcontainers, API ngoài, FE, E2E, load), các test bắt buộc cho bất biến oversell, tiền, máy trạng thái, nhánh bù trừ, idempotency, bảo mật; test xác định, dữ liệu test, định dạng test case trong docs/test/. Dùng khi viết test, lập test case, verify AC hoặc review test."
---

# Ticket Hub: quy tắc test case

Đọc kèm skill `verification` (bằng chứng trước khi báo xong) và `ticket-hub-coding-conventions`. Tầng test và công cụ theo `overview.md` §20.

## 1. Mỗi test gắn với yêu cầu

- Test cho quy tắc nghiệp vụ ghi mã BR / AC trong tên hoặc `@DisplayName` / `describe`: `BR-03: booking hết hạn sau 10 phút thì nhả ghế`.
- Mỗi AC của task có ít nhất một test tự động, trừ khi ghi rõ lý do chỉ test tay.
- Cấu trúc **Given / When / Then**; một test kiểm **một hành vi**.

## 2. Chọn tầng test

| Tầng | Dùng cho | Công cụ |
|---|---|---|
| Unit | Domain, máy trạng thái, tính phí, quy tắc BR | JUnit, AssertJ, Instancio |
| Kiến trúc | Ranh giới module | Spring Modulith `verify()`, ArchUnit |
| Integration | Repository, SQL khoá ghế, Lua, listener, job, webhook, nhánh bù trừ | Testcontainers (Postgres, Redis, Keycloak), Awaitility |
| API ngoài | Stripe, webhook có chữ ký | stripe-mock, WireMock |
| Frontend | Component, hook, form | Vitest, React Testing Library, MSW |
| E2E | Luồng chính end-to-end | Playwright |
| Load / chaos | Bất biến dưới tải, chịu lỗi | JMeter, Chaos Mesh |

- **Không dùng H2** hay DB giả: integration test chạy trên Postgres thật qua Testcontainers.
- Không mock thứ mình sở hữu chỉ để test dễ (repository, module khác); mock ở biên ngoài (Stripe, email).

## 3. Test bắt buộc theo vùng rủi ro

**Chống oversell (BR-01)**
- Test đồng thời: N luồng (≥ 50) cùng giữ một ghế → đúng 1 thành công, N−1 nhận `SEAT_UNAVAILABLE`.
- Hai booking giữ tập ghế chồng nhau theo thứ tự khác nhau → không deadlock treo, không bán trùng.
- Sau mỗi test đồng thời / load test chạy truy vấn ghế trùng ở `overview.md` §10, phải trả 0 dòng.

**Tiền (BR-06, BR-07, BR-08, BR-10)**
- Phí làm tròn xuống, VND không nhân 100; số tiền hoàn BR-07 (trừ phí) và BR-08 (100%).

**Máy trạng thái (`overview.md` §7.7)**
- Mỗi chuyển trạng thái hợp lệ có test; ít nhất một chuyển không hợp lệ cho mỗi trạng thái bị từ chối / bỏ qua.

**Nhánh bù trừ (`overview.md` §7.3, §7.4)**
- Mỗi dòng của §7.3 có integration test: hết hạn, khách huỷ, thanh toán thất bại, thanh toán đến muộn, **mất webhook** (đối soát sửa được), webhook sai thứ tự, phát vé vi phạm UNIQUE, hoàn thất bại.

**Idempotency (§6.7)**
- Gửi lại cùng key → cùng response, không tạo bản ghi mới; khác body → 422; đang xử lý → 409.
- Consumer nhận cùng event 2 lần → kết quả như 1 lần.

**Bảo mật**
- Chưa đăng nhập → 401; sai role → 403; truy cập tài nguyên của người khác (IDOR) → 403 / 404; token sai `aud` bị từ chối.
- Webhook sai chữ ký hoặc lệch thời gian bị từ chối.

## 4. Test phải xác định

- Không `Thread.sleep`; chờ bất đồng bộ bằng Awaitility (backend) hoặc `findBy…` / `waitFor` (frontend).
- Thời gian qua `Clock` cố định / điều chỉnh được; không phụ thuộc giờ máy chạy test.
- Mỗi test tự tạo dữ liệu của nó (builder, Instancio), không phụ thuộc thứ tự chạy hay dữ liệu test khác để lại.
- Test flaky là bug: sửa hoặc cách ly kèm lý do, không bỏ qua im lặng.

## 5. Frontend và E2E

- RTL tìm phần tử theo role / label / text như người dùng thấy; `data-testid` chỉ khi không có cách khác.
- MSW giả lập API theo đúng schema OpenAPI; có case lỗi Problem Details.
- Không viết test chỉ có snapshot.
- E2E chỉ cho luồng chính: đăng nhập theo role, mua vé bằng thẻ test Stripe, hoàn vé, check-in. Selector ổn định (`getByRole`).

## 6. Sửa bug

Viết test tái hiện **đỏ trước**, sửa, rồi thấy **xanh** (red–green, skill `verification`). Test này giữ lại làm test hồi quy.

## 7. Test case thủ công / tài liệu test

Test case cần ghi thành tài liệu (verify cuối sprint, test tay) đặt ở `docs/test/<sprint-hoặc-feature>.md`:

| ID | BR / AC | Tiền điều kiện | Bước | Kết quả mong đợi | Loại | Tự động? |
|---|---|---|---|---|---|---|
| TC-BK-001 | BR-03, AC-2 | Sự kiện `ON_SALE`, user đã đăng nhập | Giữ 2 ghế, chờ quá hạn | Booking `EXPIRED`, ghế `AVAILABLE` | Integration | Có (`BookingExpiryIT`) |

- ID theo module: `TC-AC-` (account), `TC-CT-` (catalog), `TC-BK-` (booking), `TC-PM-` (payment), `TC-TK-` (ticket), `TC-NT-` (notification), `TC-QU-` (queue), `TC-E2E-`.
- Có case thành công, case biên (đúng 6 ghế, còn đúng 90 giây…) và case lỗi.

## 8. Độ phủ

JaCoCo theo dõi độ phủ cho `domain` và `application`, mục tiêu khoảng 80%. Không viết test vô nghĩa để tăng con số; các vùng ở mục 3 phải phủ đủ bất kể con số.
