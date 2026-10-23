import { expect, type Page, test } from '@playwright/test'

import { json, mockSession } from './support/mock-api'

/**
 * Phòng chờ (plan Sprint 7 hạng mục 8; BR-04; AC-3, AC-4): SSE được giả bằng một đáp án `text/event-stream` trọn vẹn
 * (trình duyệt tự nối lại theo `retry`), nên không cần stack thật. SSE đa pod và Gateway thật nằm ở S7-4.
 */
const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'

const eventDetail = {
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
  highDemand: true,
  tiers: [{ id: 't1', name: 'VIP', priceMinor: 2_500_000, currency: 'VND', available: 3 }],
}

const seatMap = { eventId: EVENT_ID, eventStatus: 'ON_SALE', width: 100, height: 100, tiers: [], sections: [], elements: [], seats: [] }

interface QueueMock {
  /** Vị trí hiện tại; `join` và `status` đều trả số này. */
  position: number
  admitted: boolean
  /** Luồng SSE đứt (mạng mất): mọi lần nối đều bị huỷ. */
  streamDown: boolean
  calls: { join: number; status: number; stream: number }
}

function sse(events: string): { status: number; headers: Record<string, string>; body: string } {
  // `retry` ngắn để trình duyệt nối lại nhanh sau khi đáp án kết thúc.
  return { status: 200, headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' }, body: `retry: 300\n${events}` }
}

async function mockQueue(page: Page, initial: Partial<QueueMock> = {}): Promise<QueueMock> {
  const state: QueueMock = { position: 200, admitted: false, streamDown: false, calls: { join: 0, status: 0, stream: 0 }, ...initial }
  const response = () =>
    state.admitted
      ? { phase: 'ADMITTED', admissionExpiresAt: new Date(Date.now() + 15 * 60_000).toISOString() }
      : { phase: 'QUEUED', position: state.position, estimatedWaitSeconds: 600 }

  await mockSession(page)
  await page.route('**/api/v1/administrative-units', (route) => json(route, []))
  await page.route('**/api/v1/me/notifications/unread-count', (route) => json(route, { count: 0 }))
  await page.route(`**/api/v1/events/${EVENT_ID}`, (route) => json(route, eventDetail))
  await page.route(`**/api/v1/events/${EVENT_ID}/seats`, (route) => json(route, seatMap))
  await page.route(`**/api/v1/queue/${EVENT_ID}/join`, (route) => {
    state.calls.join++
    return json(route, response(), 202)
  })
  await page.route(`**/api/v1/queue/${EVENT_ID}/status`, (route) => {
    state.calls.status++
    return json(route, response())
  })
  await page.route(`**/api/v1/queue/${EVENT_ID}/stream`, (route) => {
    state.calls.stream++
    if (state.streamDown) return route.abort('connectionreset')
    return route.fulfill(sse(`event: position\ndata: ${JSON.stringify(response())}\n\n`))
  })
  return state
}

test('SSE đứt đúng lúc được gọi tên → nối lại nhận ADMITTED ngay, tự sang chọn ghế (AC-4)', async ({ page }) => {
  const queue = await mockQueue(page)

  await page.goto(`/events/${EVENT_ID}/queue`)
  await expect(page.getByText('Đang trong hàng chờ')).toBeVisible()
  await expect(page.getByText('199')).toBeVisible()
  // Dev bật StrictMode nên effect chạy hai lần và `join` có thể bị gọi hai lần lúc mở trang (BE idempotent); chỉ cần
  // lần nối lại sau đó không xếp hàng lại.
  const joinsAfterLoad = queue.calls.join

  // Được gọi tên trong lúc luồng đang đứt: lần nối lại hỏi status và nhận ADMITTED.
  queue.admitted = true
  await expect(page.getByText('Đến lượt bạn')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Chọn ghế' }).or(page.getByRole('link', { name: 'Chọn ghế' }))).toBeVisible()

  await expect(page).toHaveURL(new RegExp(`/events/${EVENT_ID}/seats$`))
  expect(queue.calls.join).toBe(joinsAfterLoad)
})

test('mất mạng rồi có lại → vị trí cập nhật, không xếp hàng lại (AC-2)', async ({ page }) => {
  const queue = await mockQueue(page)

  await page.goto(`/events/${EVENT_ID}/queue`)
  await expect(page.getByText('199')).toBeVisible()
  const joinsAfterLoad = queue.calls.join

  queue.streamDown = true
  await page.waitForTimeout(800)
  queue.position = 120
  queue.streamDown = false

  await expect(page.getByText('119')).toBeVisible()
  expect(queue.calls.join).toBe(joinsAfterLoad)
  expect(queue.calls.status).toBeGreaterThan(0)
})

test('phòng chờ đóng (sự kiện tạm dừng) → hiện lý do', async ({ page }) => {
  await mockQueue(page)
  await page.route(`**/api/v1/queue/${EVENT_ID}/stream`, (route) => route.fulfill(sse('event: closed\ndata: {"reason":"SUSPENDED"}\n\n')))

  await page.goto(`/events/${EVENT_ID}/queue`)

  await expect(page.getByText('Sự kiện tạm dừng bán')).toBeVisible()
})
