import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { vi } from 'vitest'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const confirmPayment = vi.hoisted(() => vi.fn())

// Stripe.js không chạy trong jsdom: thay Payment Element bằng khung giả, giữ nguyên logic của form.
vi.mock('./stripe', () => ({ getStripe: () => Promise.resolve({}) }))
vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  PaymentElement: () => <div data-testid="payment-element" />,
  useStripe: () => ({ confirmPayment }),
  useElements: () => ({}),
}))

const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'
const BOOKING_ID = '00000000-0000-7000-8000-0000000a0001'

const booking = (overrides: Record<string, unknown> = {}) => ({
  id: BOOKING_ID,
  eventId: EVENT_ID,
  status: 'PENDING_PAYMENT',
  expiresAt: new Date(Date.now() + 300_000).toISOString(),
  totalMinor: 5_000_000,
  currency: 'VND',
  items: [
    { seatId: 's1', sectionCode: 'A', row: 'B', number: 7, tierId: 't1', tierName: 'VIP', priceMinor: 2_500_000 },
    { seatId: 's2', sectionCode: 'A', row: 'B', number: 8, tierId: 't1', tierName: 'VIP', priceMinor: 2_500_000 },
  ],
  ...overrides,
})

const payment = { paymentId: 'p1', clientSecret: 'cs_test', amountMinor: 5_000_000, platformFeeMinor: 250_000, currency: 'VND' }

function problem(status: number, code: string) {
  return HttpResponse.json(
    { type: 'about:blank', title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function mockBase(getBooking: () => Record<string, unknown>) {
  mockMe(meResponse(['CUSTOMER']))
  server.use(
    http.get('/api/v1/administrative-units', () => HttpResponse.json([])),
    http.get(`/api/v1/events/${EVENT_ID}`, () =>
      HttpResponse.json({
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
    ),
    http.get(`/api/v1/bookings/${BOOKING_ID}`, () => HttpResponse.json(getBooking())),
    http.get('/api/v1/me/tickets', () => HttpResponse.json([])),
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

beforeEach(() => confirmPayment.mockReset())

describe('thanh toán (BR-03, BR-07)', () => {
  it('POST /payments kèm Idempotency-Key, hiện phí nền tảng không hoàn lại và nút thanh toán', async () => {
    mockBase(() => booking())
    const keys: (string | null)[] = []
    server.use(
      http.post('/api/v1/payments', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        return HttpResponse.json(payment, { status: 201 })
      }),
    )
    renderAt(`/checkout/${BOOKING_ID}`)

    expect(await screen.findByRole('button', { name: /Thanh toán 5\.000\.000/ }, { timeout: 5000 })).toBeEnabled()
    expect(screen.getByText(/phí nền tảng 250\.000.*không hoàn lại/)).toBeInTheDocument()
    expect(screen.getByTestId('payment-element')).toBeInTheDocument()
    expect(keys).toHaveLength(1)
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('còn dưới 90 giây: không gọi POST /payments, khoá thanh toán và báo chọn lại ghế (AC-1)', async () => {
    mockBase(() => booking({ expiresAt: new Date(Date.now() + 60_000).toISOString() }))
    let called = false
    server.use(
      http.post('/api/v1/payments', () => {
        called = true
        return HttpResponse.json(payment, { status: 201 })
      }),
    )
    renderAt(`/checkout/${BOOKING_ID}`)

    expect(await screen.findByText(/Còn quá ít thời gian để thanh toán an toàn/, undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Thanh toán/ })).not.toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('server trả 409 HOLD_EXPIRING: hiện khối chọn lại ghế', async () => {
    mockBase(() => booking())
    server.use(http.post('/api/v1/payments', () => problem(409, 'HOLD_EXPIRING')))
    renderAt(`/checkout/${BOOKING_ID}`)

    expect(await screen.findByText(/Còn quá ít thời gian để thanh toán an toàn/, undefined, { timeout: 5000 })).toBeInTheDocument()
  })

  it('thẻ bị từ chối: báo ngay dưới form, form mở lại để thử thẻ khác (AC-3)', async () => {
    mockBase(() => booking())
    server.use(http.post('/api/v1/payments', () => HttpResponse.json(payment, { status: 201 })))
    confirmPayment.mockResolvedValue({ error: { type: 'card_error' } })
    const user = userEvent.setup()
    renderAt(`/checkout/${BOOKING_ID}`)

    await user.click(await screen.findByRole('button', { name: /Thanh toán 5\.000\.000/ }, { timeout: 5000 }))

    expect(await screen.findByText('Thẻ bị từ chối. Thử thẻ khác hoặc phương thức khác.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Thanh toán 5\.000\.000/ })).toBeEnabled()
  })

  it.each([
    ['REFUND_PENDING', 'Không giữ lại được ghế', /5\.000\.000.*sẽ được hoàn/],
    ['REFUNDED', 'Đã hoàn tiền', /5\.000\.000.*đã được hoàn/],
    ['REFUND_FAILED', 'Hoàn tiền chưa thành công', /gặp lỗi/],
  ])('đơn hết hạn có latePayment = %s: hiện "%s", không hiện form (AC-4)', async (latePayment, title, body) => {
    mockBase(() => booking({ status: 'EXPIRED', latePayment }))
    renderAt(`/checkout/${BOOKING_ID}`)

    expect(await screen.findByRole('heading', { name: title }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText(body)).toBeInTheDocument()
    expect(screen.queryByText('Hết thời gian giữ ghế')).not.toBeInTheDocument()
  })

  it('tải lại trang sau khi đã trả: server trả PAYMENT_ALREADY_SUCCEEDED → chờ xác nhận, không hiện lại form (AC-4)', async () => {
    let polls = 0
    mockBase(() => {
      polls++
      return booking({ status: polls > 1 ? 'CONFIRMED' : 'PENDING_PAYMENT' })
    })
    server.use(http.post('/api/v1/payments', () => problem(409, 'PAYMENT_ALREADY_SUCCEEDED')))
    const router = renderAt(`/checkout/${BOOKING_ID}`)

    expect(await screen.findByText('Đang xác nhận thanh toán', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByTestId('payment-element')).not.toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe(`/checkout/${BOOKING_ID}/success`), { timeout: 8000 })
  }, 15_000)

  it('trả tiền xong: chờ đơn CONFIRMED rồi chuyển sang trang thành công (AC-2)', async () => {
    let status = 'PENDING_PAYMENT'
    mockBase(() => booking({ status }))
    server.use(http.post('/api/v1/payments', () => HttpResponse.json(payment, { status: 201 })))
    confirmPayment.mockImplementation(() => {
      status = 'CONFIRMED'
      return Promise.resolve({})
    })
    const user = userEvent.setup()
    const router = renderAt(`/checkout/${BOOKING_ID}`)

    await user.click(await screen.findByRole('button', { name: /Thanh toán 5\.000\.000/ }, { timeout: 5000 }))

    expect(await screen.findByText('Đang xác nhận thanh toán')).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe(`/checkout/${BOOKING_ID}/success`), { timeout: 8000 })
    expect(await screen.findByText('Thanh toán thành công')).toBeInTheDocument()
  }, 15_000)
})
