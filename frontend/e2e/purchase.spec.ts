import { execFileSync } from 'node:child_process'
import path from 'node:path'

import { expect, test } from '@playwright/test'

import { login } from './support/login'
import { createSellingEvent, type SellingEvent } from './support/sell-event'

/**
 * Sprint 3 AC-2, AC-10: mua trọn một vé bằng thẻ test Stripe trên stack thật.
 *
 * Cần thêm so với E2E đăng nhập: `app` chạy với STRIPE_SECRET_KEY / STRIPE_WEBHOOK_SECRET (test mode), webhook chuyển về
 * local bằng `stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe`, Vite build với VITE_STRIPE_PUBLISHABLE_KEY,
 * profile seed. Chưa đặt E2E_STRIPE=1 thì cả file bỏ qua (CI hiện không có khoá Stripe).
 */
test.describe('mua vé trọn luồng', () => {
  test.skip(process.env.E2E_STRIPE !== '1', 'cần Stripe test key và stripe listen (E2E_STRIPE=1)')
  test.setTimeout(180_000)

  let selling: SellingEvent

  test.beforeAll(async ({ browser }) => {
    selling = await createSellingEvent(browser)
  })

  test('chọn ghế, trả bằng thẻ 4242, nhận vé QR và email; không có ghế trùng', async ({ page, request }) => {
    await login(page, 'customer', '/me/profile')
    await page.goto(`/events/${selling.id}/seats`)

    const map = page.getByRole('application', { name: 'Sơ đồ ghế' })
    await map.focus()
    await page.keyboard.press('Home')
    await page.keyboard.press('Enter')
    await page.getByRole('button', { name: 'Tiếp tục' }).click()
    await page.waitForURL(/\/checkout\/[0-9a-f-]+$/)

    const card = page.frameLocator('iframe[title*="Secure payment"]').first()
    await card.locator('[name="number"]').fill('4242424242424242')
    await card.locator('[name="expiry"]').fill('12 / 34')
    await card.locator('[name="cvc"]').fill('123')
    await page.getByRole('button', { name: /^Thanh toán/ }).click()

    await page.waitForURL(/\/checkout\/[0-9a-f-]+\/success$/, { timeout: 90_000 })
    await expect(page.getByRole('heading', { name: 'Thanh toán thành công' })).toBeVisible()

    await page.goto('/me/tickets')
    await page.getByRole('link', { name: new RegExp(selling.title) }).first().click()
    await expect(page.getByRole('img', { name: 'Mã QR của vé' })).toBeVisible()

    // Email vé trong Mailpit trong 2 phút (AC-2).
    await expect
      .poll(
        async () => {
          const response = await request.get('http://localhost:8025/api/v1/search', { params: { query: selling.title } })
          return ((await response.json()) as { messages_count: number }).messages_count
        },
        { timeout: 120_000 },
      )
      .toBeGreaterThan(0)

    expect(duplicateSeatRows()).toBe(0)
  })
})

/** Truy vấn ghế trùng (§10): số ghế có hơn một vé chưa thu hồi trong cùng sự kiện. Phải bằng 0. */
function duplicateSeatRows(): number {
  const sql =
    "SELECT count(*) FROM (SELECT event_id, seat_id FROM ticket.tickets WHERE status <> 'REVOKED' GROUP BY event_id, seat_id HAVING count(*) > 1) d"
  const compose = path.resolve(import.meta.dirname, '../../infra/docker-compose')
  const out = execFileSync(
    'docker',
    ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'tickethub', '-d', 'tickethub', '-tA', '-c', sql],
    { cwd: compose, encoding: 'utf8' },
  )
  return Number(out.trim())
}
