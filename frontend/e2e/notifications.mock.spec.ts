import { expect, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

/**
 * Thông báo trong app (plan Sprint 4 việc 8b; AC-15): chuông hiện số chưa đọc, polling 60 giây, mở popover,
 * đánh dấu đã đọc thì số về 0. Mock API ở trình duyệt, không cần stack thật.
 */
test('mua vé → chuông hiện 1 → mở popover → đánh dấu đã đọc → số về 0, polling 60 giây thấy thông báo mới', async ({ page }) => {
  const readAt: Record<string, string> = {}
  const items = [
    {
      id: 'n1',
      type: 'TICKET_ISSUED',
      title: 'Vé của bạn đã sẵn sàng',
      body: 'Hòa nhạc Mùa Thu: 2 vé. Mở mã QR để vào cổng.',
      link: '/me/tickets',
      createdAt: new Date().toISOString(),
    },
  ]
  const unread = () => items.filter((item) => !readAt[item.id]).length

  await mockSession(page)
  await page.route('**/api/v1/me/notifications/unread-count', (route) => json(route, { count: unread() }))
  await page.route('**/api/v1/me/notifications/read-all', (route) => {
    items.forEach((item) => (readAt[item.id] = new Date().toISOString()))
    return route.fulfill({ status: 204 })
  })
  await page.route(/\/api\/v1\/me\/notifications(\?.*)?$/, (route) =>
    json(route, { items: items.map((item) => ({ ...item, readAt: readAt[item.id] })) }),
  )
  await page.route('**/api/v1/me/tickets*', (route) => json(route, []))

  await page.clock.install()
  await page.goto('/me/tickets')

  const bell = page.getByRole('button', { name: 'Thông báo (1 chưa đọc)' })
  await expect(bell).toBeVisible()
  await bell.click()
  await expect(page.getByText('Vé của bạn đã sẵn sàng')).toBeVisible()
  await page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' }).click()
  await expect(page.getByRole('button', { name: 'Thông báo', exact: true })).toBeVisible()
  await page.keyboard.press('Escape')

  // Một thông báo mới đến: chuông cập nhật sau tối đa 60 giây mà không cần tải lại trang.
  items.push({ id: 'n2', type: 'REFUND_COMPLETED', title: 'Đã hoàn tiền', body: 'Đã hoàn 950.000 ₫.', link: '/me/bookings', createdAt: new Date().toISOString() })
  await page.clock.fastForward(61_000)
  await expect(page.getByRole('button', { name: 'Thông báo (1 chưa đọc)' })).toBeVisible()
})
