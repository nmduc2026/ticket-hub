import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { vi } from 'vitest'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { useSeatSelection } from '@/features/seat-map'
import { meResponse, mockMe, server } from '@/test/server'

// Canvas không chạy trong jsdom: chọn ghế đi qua lớp bàn phím của SeatMap (cùng đường với click trên canvas).
vi.mock('react-konva', () => {
  const Passthrough = ({ children }: { children?: React.ReactNode }) => <>{children}</>
  const Nothing = () => null
  return { Stage: Passthrough, Layer: Passthrough, Group: Passthrough, Rect: Nothing, Line: Nothing, Text: Nothing }
})

const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'
const BOOKING_ID = '00000000-0000-7000-8000-0000000a0001'
const TIER = { id: 't1', name: 'VIP', priceMinor: 2_500_000, currency: 'VND', available: 3 }

const detail = {
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
  tiers: [TIER],
}

function seatsResponse(statuses: Record<string, string> = {}) {
  return {
    eventId: EVENT_ID,
    eventStatus: 'ON_SALE',
    width: 1200,
    height: 800,
    tiers: [TIER],
    sections: [{ code: 'A', name: 'Khu A', shape: {}, tierId: 't1' }],
    elements: [],
    seats: [1, 2, 3].map((number) => ({
      id: `s${number}`,
      sectionCode: 'A',
      row: 'B',
      number,
      x: 100 + number * 24,
      y: 200,
      status: statuses[`s${number}`] ?? 'AVAILABLE',
    })),
  }
}

function problem(status: number, code: string) {
  return HttpResponse.json(
    { type: 'about:blank', title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function mockCatalog(seats: () => ReturnType<typeof seatsResponse> = () => seatsResponse()) {
  mockMe(meResponse(['CUSTOMER']))
  server.use(
    http.get('/api/v1/administrative-units', () => HttpResponse.json([])),
    http.get(`/api/v1/events/${EVENT_ID}`, () => HttpResponse.json(detail)),
    http.get(`/api/v1/events/${EVENT_ID}/seats`, () => HttpResponse.json(seats())),
  )
}

function renderAt(path: string) {
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [path] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

async function selectFirstSeat(user: ReturnType<typeof userEvent.setup>) {
  const map = await screen.findByRole('application', { name: 'Sơ đồ ghế' }, { timeout: 5000 })
  map.focus()
  await user.keyboard('{Home}{Enter}')
}

beforeEach(() => useSeatSelection.setState({ eventId: null, selectedIds: new Set() }))

describe('giữ ghế (AC-6, AC-7)', () => {
  it('bấm Tiếp tục gửi POST /bookings kèm Idempotency-Key rồi chuyển sang màn giữ ghế', async () => {
    mockCatalog()
    const headers: Headers[] = []
    let body: unknown
    server.use(
      http.post('/api/v1/bookings', async ({ request }) => {
        headers.push(request.headers)
        body = await request.json()
        return HttpResponse.json(
          { bookingId: BOOKING_ID, expiresAt: new Date(Date.now() + 600_000).toISOString(), totalMinor: 2_500_000, currency: 'VND' },
          { status: 201 },
        )
      }),
      http.get(`/api/v1/bookings/${BOOKING_ID}`, () =>
        HttpResponse.json({
          id: BOOKING_ID,
          eventId: EVENT_ID,
          status: 'PENDING_PAYMENT',
          expiresAt: new Date(Date.now() + 600_000).toISOString(),
          totalMinor: 2_500_000,
          currency: 'VND',
          items: [{ seatId: 's1', sectionCode: 'A', row: 'B', number: 1, tierId: 't1', tierName: 'VIP', priceMinor: 2_500_000 }],
        }),
      ),
    )
    const user = userEvent.setup()
    const router = renderAt(`/events/${EVENT_ID}/seats`)
    await selectFirstSeat(user)

    expect(screen.getByRole('button', { name: 'Bỏ chọn Hàng B, ghế 1' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`/checkout/${BOOKING_ID}`), { timeout: 5000 })
    expect(body).toEqual({ eventId: EVENT_ID, seatIds: ['s1'] })
    expect(headers[0].get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/)
    expect(await screen.findByRole('timer')).toHaveTextContent(/^(09:5\d|10:00)$/)
  })

  it('409 SEAT_UNAVAILABLE: báo ngay trong trang, bỏ ghế đã mất và lần bấm sau dùng key mới', async () => {
    let seatsNow = seatsResponse()
    mockCatalog(() => seatsNow)
    const keys: string[] = []
    server.use(
      http.post('/api/v1/bookings', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '')
        seatsNow = seatsResponse({ s1: 'HELD' })
        return problem(409, 'SEAT_UNAVAILABLE')
      }),
    )
    const user = userEvent.setup()
    renderAt(`/events/${EVENT_ID}/seats`)
    await selectFirstSeat(user)
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))

    expect(await screen.findByText('Ghế vừa có người giữ. Chọn ghế khác.')).toBeInTheDocument()
    expect(await screen.findByText(/Hàng B, ghế 1 vừa có người giữ/)).toBeInTheDocument()
    expect(screen.getByText('Chưa chọn ghế')).toBeInTheDocument()

    screen.getByRole('application', { name: 'Sơ đồ ghế' }).focus()
    await user.keyboard('{ArrowRight}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    await waitFor(() => expect(keys).toHaveLength(2))
    expect(keys[0]).not.toBe(keys[1])
  })

  it('lỗi 503 rồi bấm lại: dùng đúng Idempotency-Key cũ (không tạo đơn thứ hai)', async () => {
    mockCatalog()
    const keys: string[] = []
    server.use(
      http.post('/api/v1/bookings', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '')
        return keys.length === 1 ? problem(503, 'UNAVAILABLE') : problem(409, 'EVENT_NOT_ON_SALE')
      }),
    )
    const user = userEvent.setup()
    renderAt(`/events/${EVENT_ID}/seats`)
    await selectFirstSeat(user)

    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    expect(await screen.findByText('Máy chủ đang bận. Thử lại sau ít phút.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    await waitFor(() => expect(keys).toHaveLength(2))
    expect(keys[1]).toBe(keys[0])
  })

  it('422 TICKET_LIMIT_EXCEEDED hiện số vé tối đa của sự kiện và giữ nguyên lựa chọn', async () => {
    mockCatalog()
    server.use(http.post('/api/v1/bookings', () => problem(422, 'TICKET_LIMIT_EXCEEDED')))
    const user = userEvent.setup()
    renderAt(`/events/${EVENT_ID}/seats`)
    await selectFirstSeat(user)
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))

    expect(await screen.findByText(/tối đa 6 vé mỗi người/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bỏ chọn Hàng B, ghế 1' })).toBeInTheDocument()
  })

  it('mất kết nối khi cập nhật ghế: hiện banner và khoá nút Tiếp tục', async () => {
    let fail = false
    mockCatalog(() => seatsResponse())
    server.use(
      http.get(`/api/v1/events/${EVENT_ID}/seats`, () =>
        fail ? HttpResponse.error() : HttpResponse.json(seatsResponse()),
      ),
    )
    const user = userEvent.setup()
    renderAt(`/events/${EVENT_ID}/seats`)
    await selectFirstSeat(user)
    expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeEnabled()

    fail = true
    await user.click(await screen.findByRole('button', { name: 'Phóng to' }))
    window.dispatchEvent(new Event('focus'))
    document.dispatchEvent(new Event('visibilitychange'))
    // Polling 5 giây: chờ vòng kế tiếp thấy lỗi.
    expect(await screen.findByText(/Mất kết nối mạng/, undefined, { timeout: 20_000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeDisabled()
  }, 30_000)
})

describe('màn giữ ghế (BR-03, BR-12)', () => {
  const booking = (overrides: Record<string, unknown> = {}) => ({
    id: BOOKING_ID,
    eventId: EVENT_ID,
    status: 'PENDING_PAYMENT',
    expiresAt: new Date(Date.now() + 125_000).toISOString(),
    totalMinor: 5_000_000,
    currency: 'VND',
    items: [
      { seatId: 's1', sectionCode: 'A', row: 'B', number: 7, tierId: 't1', tierName: 'VIP', priceMinor: 2_500_000 },
      { seatId: 's2', sectionCode: 'A', row: 'B', number: 8, tierId: 't1', tierName: 'VIP', priceMinor: 2_500_000 },
    ],
    ...overrides,
  })

  it('hiện đồng hồ theo expiresAt, ghế đang giữ và tổng tiền', async () => {
    mockCatalog()
    server.use(http.get(`/api/v1/bookings/${BOOKING_ID}`, () => HttpResponse.json(booking())))
    renderAt(`/checkout/${BOOKING_ID}`)

    expect(await screen.findByRole('timer', undefined, { timeout: 5000 })).toHaveTextContent(/^02:0[3-5]$/)
    expect(screen.getByText('VIP · Hàng B · Ghế 7')).toBeInTheDocument()
    expect(screen.getByText('VIP · Hàng B · Ghế 8')).toBeInTheDocument()
    expect(screen.getByText('2 vé')).toBeInTheDocument()
    expect(screen.getAllByText(/5\.000\.000/).length).toBeGreaterThan(0)
  })

  it('"Huỷ và chọn lại ghế" gọi DELETE rồi quay về chọn ghế', async () => {
    mockCatalog()
    let deleted = false
    server.use(
      http.get(`/api/v1/bookings/${BOOKING_ID}`, () => HttpResponse.json(booking())),
      http.delete(`/api/v1/bookings/${BOOKING_ID}`, () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    const router = renderAt(`/checkout/${BOOKING_ID}`)
    await user.click(await screen.findByRole('button', { name: 'Huỷ và chọn lại ghế' }, { timeout: 5000 }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`/events/${EVENT_ID}/seats`), { timeout: 5000 })
    expect(deleted).toBe(true)
  })

  it('409 BOOKING_NOT_CANCELLABLE: báo trong trang và tải lại trạng thái đơn', async () => {
    mockCatalog()
    let status = 'PENDING_PAYMENT'
    server.use(
      http.get(`/api/v1/bookings/${BOOKING_ID}`, () => HttpResponse.json(booking({ status }))),
      http.delete(`/api/v1/bookings/${BOOKING_ID}`, () => {
        status = 'EXPIRED'
        return problem(409, 'BOOKING_NOT_CANCELLABLE')
      }),
    )
    const user = userEvent.setup()
    renderAt(`/checkout/${BOOKING_ID}`)
    await user.click(await screen.findByRole('button', { name: 'Huỷ và chọn lại ghế' }, { timeout: 5000 }))

    expect(await screen.findByText('Hết thời gian giữ ghế')).toBeInTheDocument()
  })

  it('đơn đã EXPIRED: hiện trạng thái hết giờ và nút chọn lại ghế', async () => {
    mockCatalog()
    server.use(http.get(`/api/v1/bookings/${BOOKING_ID}`, () => HttpResponse.json(booking({ status: 'EXPIRED' }))))
    renderAt(`/checkout/${BOOKING_ID}`)

    expect(await screen.findByRole('heading', { name: 'Hết thời gian giữ ghế' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chọn lại ghế' })).toBeInTheDocument()
  })

  it('đơn của người khác hoặc không có: 404 hiện "Không tìm thấy đơn"', async () => {
    mockCatalog()
    server.use(http.get(`/api/v1/bookings/${BOOKING_ID}`, () => problem(404, 'BOOKING_NOT_FOUND')))
    renderAt(`/checkout/${BOOKING_ID}`)
    expect(await screen.findByRole('heading', { name: 'Không tìm thấy đơn' }, { timeout: 5000 })).toBeInTheDocument()
  })
})
