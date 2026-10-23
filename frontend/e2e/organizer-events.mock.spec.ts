import { expect, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

const VENUES = [
  { id: 'v1', name: 'Nhà hát Hòa Bình', address: '240 Ba Tháng Hai', wardName: 'Phường 12', cityName: 'TP.HCM', capacity: 500 },
  { id: 'v2', name: 'Sân vận động Mỹ Đình', address: 'Lê Đức Thọ', wardName: 'Mỹ Đình', cityName: 'Hà Nội', capacity: 40000 },
]

const ALL_EVENTS = Array.from({ length: 40 }, (_, index) => ({
  id: `e${index}`,
  title: `Sự kiện ${index + 1}`,
  status: index % 2 === 0 ? 'DRAFT' : 'ON_SALE',
  startsAt: '2026-11-15T13:00:00Z',
  venueId: VENUES[index % 2].id,
  venueName: VENUES[index % 2].name,
}))

/** Sprint 2 AC-12 (việc 9b): bộ lọc ghi lên URL, tải lại giữ nguyên, toolbar ghim khi cuộn, "Xuất (n)". */
test.describe('danh sách sự kiện của organizer', () => {
  test.beforeEach(async ({ page }) => {
    await mockSession(page)
    await page.route('**/api/v1/venues', (route) => json(route, VENUES))
    await page.route('**/api/v1/organizer/events?**', (route) => {
      const url = new URL(route.request().url())
      const statuses = url.searchParams.getAll('status')
      const venue = url.searchParams.get('venueId')
      const q = url.searchParams.get('q')?.toLowerCase()
      const size = Number(url.searchParams.get('size') ?? 10)
      const matches = ALL_EVENTS.filter(
        (event) =>
          (statuses.length === 0 || statuses.includes(event.status)) &&
          (!venue || event.venueId === venue) &&
          (!q || event.title.toLowerCase().includes(q)),
      )
      return json(route, { items: matches.slice(0, size), total: matches.length, page: 0, size })
    })
  })

  test('tải lại trang giữ bộ lọc và từ khoá', async ({ page }) => {
    await page.goto('/organizer/events')
    await expect(page.getByRole('cell', { name: 'Sự kiện 1', exact: true })).toBeVisible()

    await page.getByRole('button', { name: /Trạng thái/ }).click()
    await page.getByRole('checkbox', { name: 'Đang mở bán' }).click()
    await page.keyboard.press('Escape')
    await page.getByRole('searchbox').fill('Sự kiện 2')

    await expect(page).toHaveURL(/status=%5B%22ON_SALE%22%5D/)
    await expect(page).toHaveURL(/q=S/)
    await expect(page.getByRole('cell', { name: 'Sự kiện 2', exact: true })).toBeVisible()

    await page.reload()

    await expect(page.getByRole('button', { name: /Trạng thái/ })).toContainText('Đang mở bán')
    await expect(page.getByRole('searchbox')).toHaveValue('Sự kiện 2')
    await expect(page.getByRole('cell', { name: 'Sự kiện 2', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Sự kiện 1', exact: true })).toBeHidden()

    await page.getByRole('button', { name: 'Đặt lại' }).click()
    await expect(page).toHaveURL(/\/organizer\/events$/)
  })

  test('cuộn thì toolbar ghim dưới topbar; chọn dòng hiện "Xuất (n)"', async ({ page }) => {
    await page.goto('/organizer/events?size=50')
    await expect(page.getByRole('cell', { name: 'Sự kiện 40', exact: true })).toBeAttached()

    const toolbar = page.getByTestId('list-toolbar')
    const topbar = page.getByRole('banner')
    await page.mouse.wheel(0, 1200)
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(300)

    const toolbarBox = await toolbar.boundingBox()
    const topbarBox = await topbar.boundingBox()
    expect(toolbarBox).not.toBeNull()
    expect(topbarBox).not.toBeNull()
    expect(Math.round(toolbarBox!.y)).toBe(Math.round(topbarBox!.y + topbarBox!.height))

    await page.getByRole('checkbox', { name: 'Chọn dòng Sự kiện 40' }).check()
    await page.getByRole('checkbox', { name: 'Chọn dòng Sự kiện 39' }).check()
    await expect(page.getByRole('button', { name: 'Xuất (2)' })).toBeVisible()
  })
})
