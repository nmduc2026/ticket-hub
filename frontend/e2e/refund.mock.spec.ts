import { expect, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

const BOOKING_ID = '00000000-0000-7000-8000-0000000a0002'
const TICKET_ID = '00000000-0000-7000-8000-0000000c0001'
const DAY = 86_400_000

/**
 * Hoàn vé (plan Sprint 4 việc 7; BR-07, BR-09): yêu cầu hoàn ở "Đơn hàng của tôi" → vé biến khỏi tab "Sắp diễn ra" của
 * "Vé của tôi" và sang "Đã hoàn". Mock API ở trình duyệt, không cần stack thật.
 */
test('yêu cầu hoàn vé: đơn chuyển sang đang hoàn và vé biến khỏi Vé của tôi', async ({ page }) => {
  let refunded = false
  await mockSession(page)
  await page.route('**/api/v1/me/bookings*', (route) =>
    json(route, [
      {
        bookingId: BOOKING_ID,
        eventTitle: 'Hòa nhạc Mùa Thu',
        startsAt: new Date(Date.now() + 30 * DAY).toISOString(),
        status: refunded ? 'REFUND_REQUESTED' : 'CONFIRMED',
        totalMinor: 1_000_000,
        currency: 'VND',
        createdAt: new Date(Date.now() - DAY).toISOString(),
        ticketCount: 1,
        platformFeeMinor: 50_000,
        ...(refunded
          ? { refundStatus: 'REQUESTED', refundAmountMinor: 950_000 }
          : { refundableAmountMinor: 950_000, refundDeadline: new Date(Date.now() + 28 * DAY).toISOString() }),
      },
    ]),
  )
  await page.route('**/api/v1/me/tickets*', (route) =>
    json(route, [
      {
        ticketId: TICKET_ID,
        eventTitle: 'Hòa nhạc Mùa Thu',
        startsAt: new Date(Date.now() + 30 * DAY).toISOString(),
        venueName: 'Nhà hát Hòa Bình',
        seat: { sectionCode: 'A', row: 'B', number: 7 },
        tierName: 'VIP',
        status: refunded ? 'REVOKED' : 'VALID',
        qrToken: 'header.payload.signature',
      },
    ]),
  )
  await page.route(`**/api/v1/bookings/${BOOKING_ID}/refund`, (route) => {
    refunded = true
    return json(route, { bookingId: BOOKING_ID, status: 'REFUND_REQUESTED', refundAmountMinor: 950_000 }, 202)
  })

  await page.goto('/me/tickets')
  await expect(page.getByRole('tab', { name: 'Sắp diễn ra (1)' })).toBeVisible()

  await page.goto('/me/bookings')
  await page.getByRole('button', { name: 'Yêu cầu hoàn vé' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('950.000 ₫')).toBeVisible()
  await dialog.getByRole('button', { name: 'Gửi yêu cầu hoàn' }).click()
  await expect(page.getByText('Đang hoàn tiền')).toBeVisible()

  await page.goto('/me/tickets')
  await expect(page.getByRole('tab', { name: 'Sắp diễn ra (0)' })).toBeVisible()
  await page.getByRole('tab', { name: 'Đã hoàn (1)' }).click()
  await expect(page.getByText('Hòa nhạc Mùa Thu')).toBeVisible()
})
