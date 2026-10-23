import { expect, test } from '@playwright/test'

import { login } from './support/login'

/** Sprint 1 AC-1, AC-2, AC-4, AC-6: đăng nhập 3 role, cookie, chặn role, đăng xuất. */
test.describe('đăng nhập qua BFF', () => {
  test('người chưa đăng nhập vào /admin được đưa tới Keycloak rồi quay lại đúng trang', async ({ page }) => {
    await page.goto('/admin')
    await expect(page.locator('#username')).toBeVisible()

    await page.locator('#username').fill('admin')
    await page.locator('#password').fill('123123')
    await page.locator('#kc-login').click()

    await page.waitForURL('**/admin')
    await expect(page.getByRole('heading', { name: 'Tổng quan' })).toBeVisible()
  })

  test('trình duyệt chỉ giữ cookie SESSION và XSRF-TOKEN của ứng dụng, không có token', async ({ page, context }) => {
    await login(page, 'customer')

    const appCookies = (await context.cookies('http://localhost:5173')).map((cookie) => cookie.name).sort()
    expect(appCookies).toEqual(['SESSION', 'XSRF-TOKEN'])
    const sessionCookie = (await context.cookies('http://localhost:5173')).find((cookie) => cookie.name === 'SESSION')
    expect(sessionCookie?.httpOnly).toBe(true)
    const stored = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }).toLowerCase())
    expect(stored).not.toContain('token')
  })

  test('CUSTOMER bị chặn ở /admin và /organizer', async ({ page }) => {
    await login(page, 'customer')

    await page.goto('/admin')
    await expect(page.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
    await page.goto('/organizer')
    await expect(page.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
  })

  test('ORGANIZER vào được /organizer nhưng bị chặn ở /admin', async ({ page }) => {
    await login(page, 'organizer', '/organizer')
    await expect(page.getByRole('heading', { name: 'Tổng quan' })).toBeVisible()

    await page.goto('/admin')
    await expect(page.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
  })

  test('ADMIN vào được /admin', async ({ page }) => {
    await login(page, 'admin', '/admin')
    await expect(page.getByRole('heading', { name: 'Tổng quan' })).toBeVisible()
  })

  test('đăng xuất xoá session ở Gateway và phiên SSO ở Keycloak', async ({ page }) => {
    await login(page, 'customer')

    await page.getByRole('button', { name: 'Menu tài khoản' }).click()
    await page.getByRole('menuitem', { name: 'Đăng xuất' }).click()
    await page.waitForURL((url) => url.pathname === '/')
    await expect(page.getByRole('button', { name: 'Đăng nhập' })).toBeVisible()

    // Phiên SSO đã hết: vào lại trang cần đăng nhập thì Keycloak đòi mật khẩu, không tự đăng nhập.
    await page.goto('/me/profile')
    await expect(page.locator('#username')).toBeVisible()
  })
})
