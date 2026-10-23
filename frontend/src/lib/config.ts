/** Trang quản lý tài khoản của Keycloak (đổi email, mật khẩu, phiên đăng nhập); Keycloak là nguồn sự thật về tài khoản. */
export const KEYCLOAK_ACCOUNT_URL: string =
  import.meta.env.VITE_KEYCLOAK_ACCOUNT_URL ?? 'http://localhost:8180/realms/tickethub/account'

/**
 * Số ghế tối đa mỗi booking (BR-02) và thời gian giữ ghế hiển thị (BR-03). Backend là nơi cưỡng chế; FE chỉ dùng để chặn
 * sớm và để câu chữ khớp. Hạn giữ thật luôn lấy từ `expiresAt` của server.
 */
export const MAX_SEATS_PER_BOOKING = 6
export const HOLD_MINUTES = 10

/** Khoá công khai của Stripe (test mode); trống thì màn thanh toán báo không tải được form. */
export const STRIPE_PUBLISHABLE_KEY: string = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? ''

/** Còn dưới số giây này thì khoá thanh toán (BR-03). Server cưỡng chế bằng HOLD_EXPIRING. */
export const MIN_PAYABLE_SECONDS = 90
