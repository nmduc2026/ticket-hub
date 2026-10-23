import { expect, type Page, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

const VENUE = { id: 'v1', name: 'Nhà hát Hòa Bình', address: '240 Ba Tháng Hai', wardName: 'Phường 12', cityName: 'TP.HCM', capacity: 500 }
const grid = (x: number, y: number, rows: number) => ({ kind: 'GRID', x, y, rotation: 0, rows, seatsPerRow: 20, curve: 0, rowGap: 26, seatGap: 24 })
const SEAT_MAP = {
  id: 'm1',
  name: 'Sơ đồ chính',
  version: 1,
  width: 1200,
  height: 800,
  locked: false,
  sections: [
    { id: 'sa', code: 'A', name: 'Khu A', seatCount: 160, shape: grid(60, 160, 8) },
    { id: 'sb', code: 'B', name: 'Khu B', seatCount: 200, shape: grid(640, 160, 10) },
    { id: 'sc', code: 'C', name: 'Khu C', seatCount: 140, shape: grid(350, 480, 7) },
  ],
  elements: [{ id: 'e1', type: 'STAGE', label: 'Sân khấu', shape: { x: 350, y: 40, w: 500, h: 60, rotation: 0 } }],
}

type Detail = Record<string, unknown> & { id: string; status: string; tiers: Tier[]; tierSections: { sectionId: string; tierId: string }[] }
interface Tier {
  id: string
  name: string
  priceMinor: number
  currency: string
}

/** Server giả có trạng thái cho một organizer: tạo, sửa, hạng vé, gán khu, publish; sự kiện đã publish hiện ở trang công khai. */
async function mockOrganizerBackend(page: Page) {
  const events = new Map<string, Detail>()
  let sequence = 0
  const tierSeats = (event: Detail, tierId: string) =>
    event.tierSections.filter((a) => a.tierId === tierId).reduce((sum, a) => sum + (SEAT_MAP.sections.find((s) => s.id === a.sectionId)?.seatCount ?? 0), 0)

  await mockSession(page)
  await page.route('**/api/v1/venues', (route) => json(route, [VENUE]))
  await page.route('**/api/v1/venues/v1/seat-maps', (route) => json(route, [SEAT_MAP]))
  await page.route('**/api/v1/administrative-units**', (route) => json(route, []))

  // Danh sách + tạo: cùng đường dẫn, khác method.
  await page.route('**/api/v1/organizer/events?**', (route) => {
    const items = [...events.values()].map((event) => ({
      id: event.id,
      title: event.title,
      status: event.status,
      startsAt: event.startsAt,
      venueId: event.venueId,
      venueName: event.venueName,
    }))
    return json(route, { items, total: items.length, page: 0, size: 10 })
  })
  await page.route('**/api/v1/organizer/events', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    sequence += 1
    const id = `00000000-0000-7000-8000-0000000d00${String(sequence).padStart(2, '0')}`
    const event: Detail = {
      id,
      title: body.title as string,
      category: body.category,
      status: 'DRAFT',
      venueId: body.venueId,
      venueName: VENUE.name,
      startsAt: body.startsAt,
      maxTicketsPerUser: 6,
      highDemand: false,
      inventoryReady: false,
      salesInfoLocked: false,
      tiers: [],
      tierSections: [],
    }
    events.set(id, event)
    return json(route, event, 201)
  })

  await page.route(/\/api\/v1\/organizer\/events\/[^/?]+$/, async (route) => {
    const id = route.request().url().split('/').pop() as string
    const event = events.get(id)
    if (!event) return json(route, { status: 404, code: 'EVENT_NOT_FOUND' }, 404)
    if (route.request().method() === 'PUT') {
      Object.assign(event, route.request().postDataJSON())
      return json(route, event)
    }
    return json(route, event)
  })
  await page.route(/\/api\/v1\/organizer\/events\/[^/]+\/ticket-tiers(\/[^/]+)?$/, async (route) => {
    const [, , , , , id, , tierId] = new URL(route.request().url()).pathname.split('/')
    const event = events.get(id) as Detail
    const method = route.request().method()
    if (method === 'POST') {
      const body = route.request().postDataJSON() as { name: string; priceMinor: number }
      const tier = { id: `t${event.tiers.length + 1}`, name: body.name, priceMinor: body.priceMinor, currency: 'VND' }
      event.tiers.push(tier)
      return json(route, tier, 201)
    }
    if (method === 'DELETE') {
      event.tiers = event.tiers.filter((tier) => tier.id !== tierId)
      event.tierSections = event.tierSections.filter((a) => a.tierId !== tierId)
      return route.fulfill({ status: 204 })
    }
    return json(route, event.tiers)
  })
  await page.route(/\/api\/v1\/organizer\/events\/[^/]+\/tier-sections$/, async (route) => {
    const id = route.request().url().split('/').slice(-2)[0]
    const event = events.get(id) as Detail
    event.tierSections = (route.request().postDataJSON() as { assignments: Detail['tierSections'] }).assignments
    return json(route, event.tierSections)
  })
  await page.route(/\/api\/v1\/organizer\/events\/[^/]+\/publish$/, async (route) => {
    const id = route.request().url().split('/').slice(-2)[0]
    const event = events.get(id) as Detail
    event.status = 'PUBLISHED'
    event.salesInfoLocked = true
    event.platformFeeBps = 500
    return json(route, event)
  })

  // Trang công khai chỉ thấy sự kiện đã publish.
  await page.route('**/api/v1/events?**', (route) => {
    const published = [...events.values()].filter((event) => event.status !== 'DRAFT')
    return json(route, {
      items: published.map((event) => ({
        id: event.id,
        title: event.title,
        category: event.category,
        startsAt: event.startsAt,
        venueName: VENUE.name,
        cityName: VENUE.cityName,
        minPriceMinor: Math.min(...event.tiers.map((tier) => tier.priceMinor)),
        currency: 'VND',
        status: event.status,
      })),
      total: published.length,
      page: 0,
      size: 12,
    })
  })
  return { events, tierSeats }
}

/** Sprint 2 AC-1, AC-12 phía giao diện: tạo sự kiện → gán khu → publish → thấy ở trang công khai. */
test.describe('organizer tạo và publish sự kiện', () => {
  test('tạo → điền thông tin → hạng vé và gán khu → publish → thấy ở trang công khai', async ({ page }, testInfo) => {
    const backend = await mockOrganizerBackend(page)
    await page.goto('/organizer/events')
    await expect(page.getByText('Chưa có sự kiện')).toBeVisible()

    // 1. Tạo sự kiện (modal).
    await page.getByRole('button', { name: 'Tạo sự kiện' }).click()
    const dialog = page.getByRole('dialog', { name: 'Tạo sự kiện mới' })
    await dialog.getByLabel(/Tên sự kiện/).fill('Đêm nhạc Rock Sài Gòn')
    await dialog.getByRole('combobox', { name: /Danh mục/ }).click()
    await page.getByRole('option', { name: 'Nhạc sống' }).click()
    await dialog.getByRole('combobox', { name: /Địa điểm/ }).click()
    await page.getByRole('option', { name: 'Nhà hát Hòa Bình' }).click()
    await dialog.getByLabel(/Ngày diễn/).fill('2026-11-15')
    await dialog.getByLabel(/Giờ bắt đầu/).fill('20:00')
    await dialog.getByRole('button', { name: 'Tạo & tiếp tục' }).click()
    await expect(page).toHaveURL(/\/organizer\/events\/.+\/edit$/)
    await expect(page.getByRole('heading', { name: 'Đêm nhạc Rock Sài Gòn' })).toBeVisible()

    // 2. Thông tin chung: mô tả, giờ kết thúc, sơ đồ ghế.
    await page.getByLabel(/^Mô tả/).fill('Chương trình kéo dài 150 phút.')
    await page.getByLabel('Kết thúc').fill('22:30')
    await page.getByRole('combobox', { name: /Sơ đồ ghế/ }).click()
    await page.getByRole('option', { name: /Sơ đồ chính/ }).click()
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
    await expect(page.getByText('Đã lưu thay đổi').first()).toBeVisible()

    // 3. Mở bán.
    await page.getByRole('link', { name: 'Mở bán' }).click()
    await page.getByLabel(/Ngày mở bán/).fill('2026-10-01')
    await page.getByLabel(/Giờ mở bán/).fill('10:00')
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
    await expect(page.getByText('Đã lưu thay đổi').first()).toBeVisible()

    // 4. Hạng vé: tạo VIP và gán Khu B ngay trong modal.
    await page.getByRole('link', { name: 'Hạng vé & giá' }).click()
    await expect(page.getByText('Còn thiếu 2 mục')).toBeVisible()
    await page.getByRole('button', { name: 'Thêm hạng vé' }).click()
    const tierDialog = page.getByRole('dialog', { name: 'Thêm hạng vé' })
    await tierDialog.getByLabel(/Tên hạng vé/).fill('VIP')
    await tierDialog.getByLabel(/^Giá/).fill('2500000')
    await tierDialog.getByRole('checkbox', { name: /Khu B/ }).click()
    await tierDialog.getByRole('button', { name: 'Lưu hạng vé' }).click()

    await expect(page.getByRole('cell', { name: 'Khu B' })).toBeVisible()
    await expect(page.getByRole('cell', { name: '2.500.000 ₫' })).toBeVisible()
    await expect(page.getByTestId('section-B')).toHaveAttribute('data-assigned', 'true')
    await expect(page.getByTestId('section-A')).toHaveAttribute('data-assigned', 'false')
    await expect(page.getByText('Đủ điều kiện')).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('tiers.png'), fullPage: true })

    // 5. Publish.
    await page.getByRole('button', { name: 'Publish' }).click()
    const confirm = page.getByRole('dialog', { name: /Publish “Đêm nhạc Rock Sài Gòn”\?/ })
    await expect(confirm.getByText('200 ghế · 1 hạng vé')).toBeVisible()
    await confirm.getByRole('button', { name: 'Publish' }).click()
    await expect(page.getByRole('button', { name: 'Publish' })).toBeHidden()
    await expect(page.getByText('Sắp mở bán').first()).toBeVisible()

    // Sau publish: thông tin bán vé khoá, tiêu đề vẫn sửa được (BR-14).
    await page.getByRole('link', { name: 'Thông tin chung' }).click()
    await expect(page.getByLabel(/Ngày diễn/)).toBeDisabled()
    await expect(page.getByLabel(/Tên sự kiện/)).toBeEnabled()
    await page.screenshot({ path: testInfo.outputPath('info-locked.png'), fullPage: true })

    // 6. Trang công khai thấy sự kiện.
    await page.goto('/events')
    await expect(page.getByText('Đêm nhạc Rock Sài Gòn').first()).toBeVisible()
    expect(backend.events.size).toBe(1)
  })
})
