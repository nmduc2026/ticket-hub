import { expect, type Page, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'
const BOOKING_ID = '00000000-0000-7000-8000-0000000a0001'
const MAP = { width: 1200, height: 800 }

// Hình học như dữ liệu seed: khu A (8 hàng), khu B (10 hàng), khu C (7 hàng, không gán hạng vé) = 500 ghế.
const SECTIONS = [
  { code: 'A', name: 'Khu A', rows: 8, x: 60, y: 160, tierId: 't1' },
  { code: 'B', name: 'Khu B', rows: 10, x: 640, y: 160, tierId: 't2' },
  { code: 'C', name: 'Khu C', rows: 7, x: 350, y: 480, tierId: undefined },
]
const TIERS = [
  { id: 't1', name: 'Thường', priceMinor: 800_000, currency: 'VND', available: 160 },
  { id: 't2', name: 'VIP', priceMinor: 2_500_000, currency: 'VND', available: 200 },
]

interface MockSeat {
  id: string
  sectionCode: string
  row: string
  number: number
  x: number
  y: number
  status: string
}

const SEATS: MockSeat[] = SECTIONS.flatMap((section) =>
  Array.from({ length: section.rows }, (_, rowIndex) =>
    Array.from({ length: 20 }, (_, index) => ({
      id: `${section.code}-${rowIndex + 1}-${index + 1}`,
      sectionCode: section.code,
      row: String.fromCharCode(65 + rowIndex),
      number: index + 1,
      x: section.x + index * 24,
      y: section.y + rowIndex * 26,
      status: section.tierId ? 'AVAILABLE' : 'NOT_FOR_SALE',
    })),
  ).flat(),
)

/** Mock có trạng thái: giữ ghế đổi ghế thành HELD, huỷ trả về AVAILABLE (BR-03, BR-12). */
async function mockBookingApi(page: Page) {
  const held = new Map<string, string[]>()
  let expiresAt = ''

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
      tiers: TIERS,
    }),
  )
  await page.route(`**/api/v1/events/${EVENT_ID}/seats`, (route) => {
    const heldIds = new Set([...held.values()].flat())
    return json(route, {
      eventId: EVENT_ID,
      eventStatus: 'ON_SALE',
      ...MAP,
      tiers: TIERS,
      sections: SECTIONS.map((section) => ({
        code: section.code,
        name: section.name,
        shape: {
          kind: 'GRID',
          x: section.x,
          y: section.y,
          rotation: 0,
          rows: section.rows,
          seatsPerRow: 20,
          curve: 0,
          rowGap: 26,
          seatGap: 24,
          numbering: 'LTR',
          hiddenSeats: [],
        },
        tierId: section.tierId,
      })),
      elements: [{ type: 'STAGE', label: 'Sân khấu', shape: { x: 350, y: 40, w: 500, h: 60, rotation: 0 } }],
      seats: SEATS.map((seat) => (heldIds.has(seat.id) ? { ...seat, status: 'HELD' } : seat)),
    })
  })
  await page.route('**/api/v1/bookings', async (route) => {
    const { seatIds } = route.request().postDataJSON() as { seatIds: string[] }
    held.set(BOOKING_ID, seatIds)
    expiresAt = new Date(Date.now() + 600_000).toISOString()
    return json(route, { bookingId: BOOKING_ID, expiresAt, totalMinor: 0, currency: 'VND' }, 201)
  })
  await page.route(`**/api/v1/bookings/${BOOKING_ID}`, (route) => {
    if (route.request().method() === 'DELETE') {
      held.delete(BOOKING_ID)
      return route.fulfill({ status: 204 })
    }
    const seatIds = held.get(BOOKING_ID) ?? []
    const items = seatIds.map((id) => {
      const seat = SEATS.find((candidate) => candidate.id === id) as MockSeat
      const tier = TIERS[seat.sectionCode === 'A' ? 0 : 1]
      return { seatId: id, sectionCode: seat.sectionCode, row: seat.row, number: seat.number, tierId: tier.id, tierName: tier.name, priceMinor: tier.priceMinor }
    })
    return json(route, {
      id: BOOKING_ID,
      eventId: EVENT_ID,
      status: 'PENDING_PAYMENT',
      expiresAt,
      totalMinor: items.reduce((sum, item) => sum + item.priceMinor, 0),
      currency: 'VND',
      items,
    })
  })
}

/** Toạ độ màn hình của ghế, tính lại khung nhìn ban đầu như `fitView` của sơ đồ. */
async function seatPoint(page: Page, seatId: string) {
  const frame = await page.getByRole('application', { name: 'Sơ đồ ghế' }).boundingBox()
  if (!frame) throw new Error('Không thấy sơ đồ ghế')
  const scale = Math.min(frame.width / MAP.width, frame.height / MAP.height, 1.5)
  const seat = SEATS.find((candidate) => candidate.id === seatId) as MockSeat
  return {
    x: frame.x + (frame.width - MAP.width * scale) / 2 + seat.x * scale,
    y: frame.y + (frame.height - MAP.height * scale) / 2 + seat.y * scale,
  }
}

/** Sprint 2 AC-5, AC-6, AC-9, AC-10 phía giao diện: chọn ghế, đồng hồ, huỷ thì ghế trống lại. */
test.describe('giữ ghế trên trình duyệt', () => {
  test.beforeEach(async ({ page }) => mockBookingApi(page))

  test('chọn 2 ghế bằng bàn phím → thấy đồng hồ; huỷ → ghế trống lại', async ({ page }) => {
    await page.goto(`/events/${EVENT_ID}/seats`)
    const map = page.getByRole('application', { name: 'Sơ đồ ghế' })
    await expect(map.locator('canvas').first()).toBeVisible()

    await map.focus()
    await page.keyboard.press('Home')
    await page.keyboard.press('Enter')
    await page.keyboard.press('ArrowRight')
    await page.keyboard.press('Enter')
    await expect(page.getByText('2 ghế', { exact: true })).toBeVisible()
    await expect(page.getByText('Tạm tính').locator('xpath=following-sibling::strong')).toContainText('1.600.000')

    await page.getByRole('button', { name: 'Tiếp tục' }).click()
    await expect(page).toHaveURL(new RegExp(`/checkout/${BOOKING_ID}$`))
    await expect(page.getByRole('timer')).toHaveText(/^(09:5\d|10:00)$/)
    await expect(page.getByText('Thường · Hàng A · Ghế 1')).toBeVisible()

    await page.getByRole('button', { name: 'Huỷ và chọn lại ghế' }).click()
    await expect(page).toHaveURL(new RegExp(`/events/${EVENT_ID}/seats$`))
    await expect(page.getByText('Chưa chọn ghế')).toBeVisible()

    // Ghế vừa huỷ chọn lại được: server trả AVAILABLE.
    await page.getByRole('application', { name: 'Sơ đồ ghế' }).focus()
    await page.keyboard.press('Home')
    await page.keyboard.press('Enter')
    await expect(page.getByText('1 ghế', { exact: true })).toBeVisible()
  })

  test('bấm vào ghế trên canvas chọn / bỏ chọn; ghế thứ 7 bị chặn; khu xám không chọn được', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name.startsWith('mobile'), 'Ghế quá nhỏ ở 390px; bàn phím đã được kiểm ở test trên')
    await page.goto(`/events/${EVENT_ID}/seats`)
    await expect(page.getByRole('application', { name: 'Sơ đồ ghế' }).locator('canvas').first()).toBeVisible()

    const first = await seatPoint(page, 'A-1-1')
    await page.mouse.click(first.x, first.y)
    await expect(page.getByText('1 ghế', { exact: true })).toBeVisible()
    await page.mouse.click(first.x, first.y)
    await expect(page.getByText('Chưa chọn ghế')).toBeVisible()

    for (let number = 1; number <= 7; number += 1) {
      const point = await seatPoint(page, `A-2-${number}`)
      await page.mouse.click(point.x, point.y)
    }
    await expect(page.getByText('6 ghế', { exact: true })).toBeVisible()
    await expect(page.getByText('Tối đa 6 ghế · bấm lại để bỏ chọn')).toHaveClass(/text-destructive/)

    const grey = await seatPoint(page, 'C-1-1')
    await page.mouse.click(grey.x, grey.y)
    await expect(page.getByText('6 ghế', { exact: true })).toBeVisible()
  })

  test('~500 ghế: phóng to liên tục vẫn mượt (dưới 50ms mỗi bước)', async ({ page }) => {
    await page.goto(`/events/${EVENT_ID}/seats`)
    await expect(page.getByRole('application', { name: 'Sơ đồ ghế' }).locator('canvas').first()).toBeVisible()

    const zoomIn = page.getByRole('button', { name: 'Phóng to' })
    const zoomOut = page.getByRole('button', { name: 'Thu nhỏ' })
    const averageMs = await page.evaluate(async () => {
      const nextFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
      const [plus, minus] = [
        document.querySelector<HTMLButtonElement>('button[aria-label="Phóng to"]'),
        document.querySelector<HTMLButtonElement>('button[aria-label="Thu nhỏ"]'),
      ]
      const steps = 20
      const start = performance.now()
      for (let step = 0; step < steps; step += 1) {
        ;(step % 2 === 0 ? plus : minus)?.click()
        await nextFrame()
      }
      return (performance.now() - start) / steps
    })
    await expect(zoomIn).toBeVisible()
    await expect(zoomOut).toBeVisible()
    console.log(`Trung bình mỗi bước zoom: ${averageMs.toFixed(1)}ms (500 ghế)`)
    expect(averageMs).toBeLessThan(50)
  })

  test('chụp sơ đồ để đối chiếu prototype', async ({ page }, testInfo) => {
    await page.goto(`/events/${EVENT_ID}/seats`)
    await expect(page.getByRole('application', { name: 'Sơ đồ ghế' }).locator('canvas').first()).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('seat-select.png'), fullPage: true })
  })
})
