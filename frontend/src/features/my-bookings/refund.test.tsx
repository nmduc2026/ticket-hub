import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const day = 86_400_000
const BOOKING_ID = '00000000-0000-7000-8000-0000000a0002'

const confirmed = (overrides: Record<string, unknown> = {}) => ({
  bookingId: BOOKING_ID,
  eventTitle: 'Hòa nhạc Mùa Thu',
  startsAt: new Date(Date.now() + 30 * day).toISOString(),
  status: 'CONFIRMED',
  totalMinor: 1_000_000,
  currency: 'VND',
  createdAt: new Date(Date.now() - day).toISOString(),
  ticketCount: 1,
  platformFeeMinor: 50_000,
  refundableAmountMinor: 950_000,
  refundDeadline: new Date(Date.now() + 28 * day).toISOString(),
  ...overrides,
})

function renderAt(path: string) {
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [path] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  server.use(http.get('/api/v1/administrative-units', () => HttpResponse.json([])))
})

describe('Hoàn vé ở Đơn hàng của tôi', () => {
  beforeEach(() => mockMe(meResponse(['CUSTOMER'])))

  it('hộp xác nhận nêu số tiền nhận lại và phí không hoàn; gửi kèm Idempotency-Key; đơn chuyển sang đang hoàn', async () => {
    let refunded = false
    let key: string | null = null
    server.use(
      http.get('/api/v1/me/bookings', () =>
        HttpResponse.json([
          refunded
            ? confirmed({ status: 'REFUND_REQUESTED', refundStatus: 'REQUESTED', refundAmountMinor: 950_000, refundableAmountMinor: undefined, refundDeadline: undefined })
            : confirmed(),
        ]),
      ),
      http.get('/api/v1/me/tickets', () => HttpResponse.json([])),
      http.post(`/api/v1/bookings/${BOOKING_ID}/refund`, ({ request }) => {
        key = request.headers.get('Idempotency-Key')
        refunded = true
        return HttpResponse.json({ bookingId: BOOKING_ID, status: 'REFUND_REQUESTED', refundAmountMinor: 950_000 }, { status: 202 })
      }),
    )
    const user = userEvent.setup()
    renderAt('/me/bookings')

    await user.click(await screen.findByRole('button', { name: 'Yêu cầu hoàn vé' }, { timeout: 5000 }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(`Yêu cầu hoàn vé đơn ${BOOKING_ID.slice(-8).toUpperCase()}`)).toBeInTheDocument()
    expect(within(dialog).getByText('1.000.000 ₫')).toBeInTheDocument()
    expect(within(dialog).getByText('50.000 ₫')).toBeInTheDocument()
    expect(within(dialog).getByText('950.000 ₫')).toBeInTheDocument()
    expect(within(dialog).getByText(/Yêu cầu đã gửi không huỷ được/)).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Gửi yêu cầu hoàn' }))

    expect(await screen.findByText('Đang hoàn tiền', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('Hoàn 950.000 ₫')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Yêu cầu hoàn vé' })).not.toBeInTheDocument()
    expect(key).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('quá mốc hoàn thì không có nút yêu cầu hoàn', async () => {
    server.use(
      http.get('/api/v1/me/bookings', () =>
        HttpResponse.json([confirmed({ refundDeadline: new Date(Date.now() - 60_000).toISOString() })]),
      ),
    )
    renderAt('/me/bookings')

    expect(await screen.findByText('Hòa nhạc Mùa Thu', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Xem vé' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Yêu cầu hoàn vé' })).not.toBeInTheDocument()
  })

  it('server báo quá hạn hoàn: hiện lý do ngay trong hộp, đơn không đổi', async () => {
    server.use(
      http.get('/api/v1/me/bookings', () => HttpResponse.json([confirmed()])),
      http.post(`/api/v1/bookings/${BOOKING_ID}/refund`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Unprocessable', status: 422, code: 'REFUND_WINDOW_CLOSED' },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const user = userEvent.setup()
    renderAt('/me/bookings')

    await user.click(await screen.findByRole('button', { name: 'Yêu cầu hoàn vé' }, { timeout: 5000 }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Gửi yêu cầu hoàn' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Đã quá hạn hoàn vé')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('"Giữ vé" đóng hộp mà không gửi gì', async () => {
    let posted = false
    server.use(
      http.get('/api/v1/me/bookings', () => HttpResponse.json([confirmed()])),
      http.post(`/api/v1/bookings/${BOOKING_ID}/refund`, () => {
        posted = true
        return HttpResponse.json({}, { status: 202 })
      }),
    )
    const user = userEvent.setup()
    renderAt('/me/bookings')

    await user.click(await screen.findByRole('button', { name: 'Yêu cầu hoàn vé' }, { timeout: 5000 }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Giữ vé' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(posted).toBe(false)
  })
})

describe('Admin: hoàn tiền lỗi', () => {
  beforeEach(() => mockMe(meResponse(['ADMIN'])))

  it('liệt kê đơn REFUND_FAILED và hoàn thủ công từng đơn', async () => {
    let retried: string | null = null
    server.use(
      http.get('/api/v1/admin/organizers', () => HttpResponse.json({ items: [], total: 0, page: 0, size: 100 })),
      http.get('/api/v1/me/notifications/unread-count', () => HttpResponse.json({ count: 0 })),
      http.get('/api/v1/admin/bookings', ({ request }) => {
        expect(new URL(request.url).searchParams.getAll('status')).toEqual(['REFUND_FAILED'])
        return HttpResponse.json({
          items: retried
            ? []
            : [
                {
                  bookingId: BOOKING_ID,
                  eventTitle: 'Hòa nhạc Mùa Thu',
                  organizerName: 'Ban tổ chức 1',
                  contactEmail: 'khach@example.com',
                  status: 'REFUND_FAILED',
                  needsReconcile: false,
                  totalMinor: 1_000_000,
                  currency: 'VND',
                  createdAt: new Date().toISOString(),
                },
              ],
          total: retried ? 0 : 1,
          page: 0,
          size: 20,
        })
      }),
      http.post(`/api/v1/admin/bookings/${BOOKING_ID}/refund`, () => {
        retried = BOOKING_ID
        return HttpResponse.json({ bookingId: BOOKING_ID, status: 'REFUND_REQUESTED', refundAmountMinor: 950_000 }, { status: 202 })
      }),
    )
    const user = userEvent.setup()
    renderAt(`/admin/orders?status=${encodeURIComponent(JSON.stringify(['REFUND_FAILED']))}`)

    expect(await screen.findByText('khach@example.com', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('1.000.000 ₫')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Hoàn thủ công' }))

    expect(await screen.findByText('Không có kết quả khớp bộ lọc', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(retried).toBe(BOOKING_ID)
  })

  it('khách thường không vào được trang Admin', async () => {
    mockMe(meResponse(['CUSTOMER']))
    renderAt('/admin/orders')

    expect(screen.queryByText('Hoàn tiền lỗi')).not.toBeInTheDocument()
    expect(await screen.findByText(/không có quyền/i, undefined, { timeout: 5000 })).toBeInTheDocument()
  })
})
