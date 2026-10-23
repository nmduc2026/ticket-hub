-- Destination charge (ADR 0022, §7.8): connected account nhận tiền của một payment; NULL = thu trên tài khoản
-- platform (chỉ organizer dữ liệu seed, chưa có Stripe Connect).
ALTER TABLE payment.payments ADD COLUMN destination_account_id TEXT;
