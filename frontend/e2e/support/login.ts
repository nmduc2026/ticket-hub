import { expect, type Page } from '@playwright/test'

export type TestUser = 'customer' | 'organizer' | 'admin'

/** Mật khẩu dev của 3 user test trong infra/keycloak/tickethub-realm.json (không dùng ngoài máy dev / CI). */
const PASSWORD = '123123'

/** Đăng nhập qua Keycloak thật và chờ quay về `returnTo` (mặc định vào thẳng trang cần đăng nhập). */
export async function login(page: Page, user: TestUser, startAt = '/me/profile'): Promise<void> {
  await page.goto(startAt)
  await page.locator('#username').fill(user)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#kc-login').click()
  await page.waitForURL((url) => url.pathname === startAt)
  await expect(page.getByRole('banner')).toBeVisible()
}
