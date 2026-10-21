import { expect, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

const EVENT_ID = '00000000-0000-7000-8000-0000000e0001'

/**
 * Màn check-in (plan Sprint 4 việc 8; docs/design/checkin.md): nhập cổng rồi bắt đầu quét; mất mạng thì có dải cảnh
 * báo. Quét QR thật bằng camera cần điện thoại thật (kiểm tay), nên ở đây chỉ kiểm phần không cần camera.
 */
test('nhập cổng, bắt đầu quét, và dải cảnh báo khi mất mạng', async ({ page, context }) => {
  await mockSession(page)
  await page.route(`**/api/v1/organizer/events/${EVENT_ID}`, (route) => json(route, { id: EVENT_ID, title: 'Đêm nhạc Rock Sài Gòn' }))
  await page.route('**/api/v1/checkin/keys', (route) => json(route, { keys: [] }))

  await page.goto(`/checkin/${EVENT_ID}`)
  const start = page.getByRole('button', { name: 'Bắt đầu quét' })
  await expect(start).toBeDisabled()

  await page.getByLabel('Cổng soát vé').fill('Cổng 2')
  await start.click()
  await expect(page.getByText('Check-in · Cổng 2')).toBeVisible()
  await expect(page.getByText('Đêm nhạc Rock Sài Gòn')).toBeVisible()

  await context.setOffline(true)
  await expect(page.getByRole('alert').filter({ hasText: 'Mất kết nối. Chưa check-in được.' })).toBeVisible()
  await context.setOffline(false)
  await expect(page.getByText('Mất kết nối. Chưa check-in được.')).toBeHidden()
})
