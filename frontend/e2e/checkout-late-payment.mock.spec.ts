import { expect, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'
const BOOKING_ID = '00000000-0000-7000-8000-0000000a0003'

/**
 * Thanh toán đến muộn và mở lại checkout (plan sprint dọn dẹp việc 5, AC-4; overview.md §7.3): đơn hết hạn có
 * `latePayment` hiện thông báo hoàn tiền; tải lại checkout của đơn đã trả không hiện lại form. Mock API ở trình duyệt.
 */
function booking(overrides: Record<string, unknown>) {
  return {
    id: BOOKING_ID,
    eventId: EVENT_ID,
    status: 'PENDING_PAYMENT',
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
    totalMinor: 1_000_000,
    currency: 'VND',
    items: [{ seatId: 's1', sectionCode: 'A', row: 'B', number: 7, tierId: 't1', tierName: 'VIP', priceMinor: 1_000_000 }],
    ...overrides,
  }
}

test.beforeEach(async ({ page }) => {
  await mockSession(page)
  await page.route(`**/api/v1/events/${EVENT_ID}`, (route) =>
    json(route, {
      id: EVENT_ID,
      title: 'Đêm nhạc Rock Sài Gòn',
      organizerId: 'o1',
      venueName: 'Nhà hát Hòa Bình',
      cityName: 'TP.HCM',
      startsAt: '2026-11-15T13:00:00Z',
      endsAt: '2026-11-15T15:30:00Z',
      saleStartsAt: '2026-10-01T00:00:00Z',
      status: 'ON_SALE',
      maxTicketsPerUser: 6,
      soldOut: false,
      tiers: [],
    }),
  )
})

test('đơn hết hạn có latePayment = REFUND_PENDING: báo không giữ lại được ghế, tiền sẽ được hoàn', async ({ page }) => {
  await page.route(`**/api/v1/bookings/${BOOKING_ID}`, (route) =>
    json(route, booking({ status: 'EXPIRED', latePayment: 'REFUND_PENDING' })),
  )

  await page.goto(`/checkout/${BOOKING_ID}`)

  await expect(page.getByRole('heading', { name: 'Không giữ lại được ghế' })).toBeVisible()
  await expect(page.getByText(/1\.000\.000.*sẽ được hoàn/)).toBeVisible()
  await expect(page.getByRole('link', { name: 'Xem đơn của tôi' })).toBeVisible()
})

test('tải lại trang sau khi đã trả: không hiện lại form, chờ xác nhận rồi sang trang thành công', async ({ page }) => {
  let polls = 0
  await page.route(`**/api/v1/bookings/${BOOKING_ID}`, (route) => {
    polls++
    return json(route, booking({ status: polls > 1 ? 'CONFIRMED' : 'PENDING_PAYMENT' }))
  })
  await page.route('**/api/v1/payments', (route) =>
    route.fulfill({
      status: 409,
      contentType: 'application/problem+json',
      body: JSON.stringify({ type: 'about:blank', title: 'Conflict', status: 409, code: 'PAYMENT_ALREADY_SUCCEEDED' }),
    }),
  )
  await page.route('**/api/v1/me/tickets*', (route) => json(route, []))

  await page.goto(`/checkout/${BOOKING_ID}`)

  await expect(page.getByText('Đang xác nhận thanh toán')).toBeVisible()
  await expect(page.getByRole('button', { name: /^Thanh toán/ })).toHaveCount(0)
  await expect(page).toHaveURL(new RegExp(`/checkout/${BOOKING_ID}/success$`))
})
