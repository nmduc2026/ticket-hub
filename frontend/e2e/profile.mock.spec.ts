import { expect, test } from '@playwright/test'

import { json, mockSession, mockUser } from './support/mock-api'

/**
 * Hồ sơ cá nhân #19 (plan sprint dọn dẹp việc 6, AC-5): sửa thông tin, xem và đăng xuất phiên khác, hộp thoại xoá tài
 * khoản. Mock API ở trình duyệt; chạy ở 1280px và 390px.
 */
test('sửa hồ sơ, đăng xuất phiên khác và mở hộp thoại xoá tài khoản', async ({ page }) => {
  const sessions = [
    { id: 's-this', ipAddress: '10.0.0.1', startedAt: '2026-10-20T01:00:00Z', lastAccessAt: '2026-10-21T01:00:00Z', current: true },
    { id: 's-other', ipAddress: '10.0.0.2', startedAt: '2026-10-19T01:00:00Z', lastAccessAt: '2026-10-20T01:00:00Z', current: false },
  ]
  let saved: unknown
  await mockSession(page)
  await page.route('**/api/v1/me/profile', async (route) => {
    if (route.request().method() === 'PUT') {
      saved = route.request().postDataJSON()
      return json(route, { userId: mockUser.id, email: mockUser.email, fullName: mockUser.fullName, phone: '0912345678' })
    }
    return json(route, { userId: mockUser.id, email: mockUser.email, fullName: mockUser.fullName })
  })
  await page.route('**/api/v1/administrative-units*', (route) => json(route, []))
  await page.route('**/api/v1/me/notification-preferences', (route) => json(route, []))
  await page.route('**/api/v1/me/notifications/unread-count', (route) => json(route, { count: 0 }))
  await page.route('**/api/v1/me/sessions', (route) => json(route, sessions))
  await page.route('**/api/v1/me/sessions/s-other', (route) => {
    sessions.splice(1, 1)
    return route.fulfill({ status: 204 })
  })

  await page.goto('/me/profile')
  await page.getByLabel('Số điện thoại').fill('0912 345 678')
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
  await expect(page.getByText('Đã lưu hồ sơ')).toBeVisible()
  expect(saved).toMatchObject({ phone: '0912 345 678' })

  await page.getByRole('tab', { name: 'Bảo mật' }).click()
  const list = page.getByRole('list', { name: 'Phiên đăng nhập' })
  await expect(list.getByText('Thiết bị này')).toBeVisible()
  await list.getByRole('button', { name: 'Đăng xuất' }).click()
  await expect(list.getByText('10.0.0.2')).toHaveCount(0)

  await page.getByRole('button', { name: 'Xoá tài khoản' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: 'Xoá tài khoản' })).toBeDisabled()
  await dialog.getByLabel(/Nhập/).fill(mockUser.email)
  await expect(dialog.getByRole('button', { name: 'Xoá tài khoản' })).toBeEnabled()
})
