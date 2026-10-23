# Runbook: xoay khoá ký QR của vé

**Khi nào:** định kỳ (đề xuất 12 tháng), hoặc ngay khi nghi lộ khoá riêng. Liên quan: ADR 0024, `overview.md` §7.5, §13.4.

## Cách hệ thống xử lý khoá

- QR là JWT ES256 có `kid`. Khoá riêng nằm ở biến môi trường `TICKET_SIGNING_JWK` (K8s Secret; Vault khi có), không nằm trong DB.
- Khi `app` khởi động, khoá hiện tại được ghi vào `ticket.signing_keys` là `ACTIVE`; khoá `ACTIVE` trước đó tự chuyển `RETIRED` nhưng giữ lại.
- `GET /api/v1/checkin/keys` trả cả khoá `ACTIVE` lẫn `RETIRED`, và server xác minh `POST /api/v1/checkins` theo `kid`. Nên vé đã phát bằng khoá cũ vẫn check-in được sau khi xoay.
- Không cần phát lại vé và không đổi gì ở frontend.

## Các bước

1. Sinh khoá mới (P-256, có `kid` duy nhất theo ngày, vd. `ticket-2027-10`). Ví dụ bằng Nimbus JOSE hoặc `step crypto jwk create`:

   ```bash
   step crypto jwk create pub.json priv.json --kty EC --crv P-256 --alg ES256 --use sig --kid ticket-2027-10 --no-password --insecure
   ```

   Giữ `priv.json` ngoài Git, không dán vào chat hay log.
2. Cập nhật Secret chứa `TICKET_SIGNING_JWK` bằng nội dung `priv.json` (một dòng JSON):

   ```bash
   kubectl -n tickethub create secret generic app-ticket --from-file=TICKET_SIGNING_JWK=priv.json --dry-run=client -o yaml | kubectl apply -f -
   ```

3. Khởi động lại `app` (rolling restart): `kubectl -n tickethub rollout restart deploy/app`. Báo trước theo `CLAUDE.md` vì là thao tác lên cụm.
4. Xác nhận:
   - `GET /api/v1/checkin/keys` (token organizer) có cả `kid` mới và `kid` cũ.
   - Trong DB: `select key_id, status from ticket.signing_keys;` → khoá mới `ACTIVE`, khoá cũ `RETIRED`.
   - Mua một vé thử: QR mới có `kid` mới (giải mã phần header của JWT).
   - Check-in một vé phát trước khi xoay: vẫn thành công.
5. Xoá `priv.json` ở máy cục bộ sau khi Secret đã áp dụng.

## Khi nghi lộ khoá riêng (khẩn)

Xoay như trên ngay. Vé cũ ký bằng khoá bị lộ vẫn xác minh được (chữ ký chỉ để loại vé giả; server quyết định vé còn dùng được hay không). Nếu cần chặn hẳn vé ký bằng khoá đã lộ: thu hồi các vé đó bằng SQL có kiểm soát (`update ticket.tickets set status = 'REVOKED' where key_id = '<kid>' and status = 'VALID'`), phát lại vé mới cho khách bị ảnh hưởng, và ghi vào postmortem.

## Hoàn tác

Đặt lại `TICKET_SIGNING_JWK` về khoá cũ và khởi động lại: khoá cũ trở lại `ACTIVE`, khoá mới thành `RETIRED`; vé đã ký bằng khoá mới vẫn xác minh được.

## Lưu ý

- Để trống `TICKET_SIGNING_JWK` chỉ dành cho dev: app sinh khoá tạm mỗi lần chạy, vé phát trước đó không xác minh được sau khi khởi động lại.
- Không xoá khoá `RETIRED` khỏi `ticket.signing_keys` khi còn vé `VALID` ký bằng khoá đó.
