import { expect, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

/**
 * Chuông thông báo realtime (plan Sprint 7 hạng mục 9; AC-7): SSE `unread` cập nhật số chưa đọc không cần tải lại;
 * SSE bị từ chối thì chuông rơi về polling 60 giây. SSE giả bằng đáp án `text/event-stream` trọn vẹn.
 */
test.beforeEach(async ({ page }) => {
  await mockSession(page)
  await page.route(/\/api\/v1\/me\/notifications(\?.*)?$/, (route) => json(route, { items: [] }))
  await page.route('**/api/v1/me/tickets*', (route) => json(route, []))
})

test('SSE mở → số chưa đọc đổi ngay khi nhận sự kiện unread', async ({ page }) => {
  await page.route('**/api/v1/me/notifications/unread-count', (route) => json(route, { count: 0 }))
  await page.route('**/api/v1/me/notifications/stream', (route) =>
    route.fulfill({
      status: 200,
      headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' },
      // Nối lại rất lâu để chuông không bị ghi đè lại trong lúc test.
      body: 'retry: 600000\nevent: unread\ndata: {"count":2}\n\n',
    }),
  )

  await page.goto('/me/tickets')

  await expect(page.getByRole('button', { name: 'Thông báo (2 chưa đọc)' })).toBeVisible()
})

test('SSE bị từ chối → chuông vẫn cập nhật bằng polling 60 giây', async ({ page }) => {
  let unread = 0
  await page.route('**/api/v1/me/notifications/unread-count', (route) => json(route, { count: unread }))
  await page.route('**/api/v1/me/notifications/stream', (route) => route.fulfill({ status: 503, body: '' }))

  await page.clock.install()
  await page.goto('/me/tickets')
  await expect(page.getByRole('button', { name: 'Thông báo', exact: true })).toBeVisible()

  unread = 1
  await page.clock.fastForward(61_000)

  await expect(page.getByRole('button', { name: 'Thông báo (1 chưa đọc)' })).toBeVisible()
})
