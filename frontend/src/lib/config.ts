/** Trang quản lý tài khoản của Keycloak (đổi email, mật khẩu, phiên đăng nhập); Keycloak là nguồn sự thật về tài khoản. */
export const KEYCLOAK_ACCOUNT_URL: string =
  import.meta.env.VITE_KEYCLOAK_ACCOUNT_URL ?? 'http://localhost:8180/realms/tickethub/account'
