import { gatewayFetch } from '@/lib/api-client'

/** Đăng nhập luôn đi qua Gateway (BFF); SPA không cầm token (overview.md §13.1). */
const LOGIN_PATH = '/oauth2/authorization/keycloak'

export function loginUrl(returnTo: string): string {
  return `${LOGIN_PATH}?returnTo=${encodeURIComponent(returnTo)}`
}

/** Chuyển trình duyệt tới Gateway; đăng nhập xong Gateway đưa người dùng về `returnTo`. */
export function redirectToLogin(returnTo: string = currentPath()): void {
  window.location.assign(loginUrl(returnTo))
}

/**
 * Đăng xuất: Gateway xoá session rồi trả địa chỉ end_session của Keycloak (để xoá cả phiên SSO); SPA điều hướng
 * tới đó, Keycloak đưa người dùng về `/`.
 */
export async function logout(): Promise<void> {
  const response = await gatewayFetch('/logout', { method: 'POST' })
  const body: unknown = response.ok ? await response.json().catch(() => null) : null
  const target = isLogoutBody(body) ? body.logoutUrl : '/'
  window.location.assign(target)
}

function currentPath(): string {
  return window.location.pathname + window.location.search
}

function isLogoutBody(value: unknown): value is { logoutUrl: string } {
  return typeof value === 'object' && value !== null && typeof (value as { logoutUrl?: unknown }).logoutUrl === 'string'
}
