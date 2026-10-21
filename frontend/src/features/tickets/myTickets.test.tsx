import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const future = new Date(Date.now() + 30 * 86_400_000).toISOString()
const past = new Date(Date.now() - 30 * 86_400_000).toISOString()

const ticket = (id: string, overrides: Record<string, unknown> = {}) => ({
  ticketId: id,
  eventTitle: 'Đêm nhạc Rock Sài Gòn',
  startsAt: future,
  venueName: 'Nhà hát Hòa Bình',
  seat: { sectionCode: 'A', row: 'B', number: 7 },
  tierName: 'VIP',
  status: 'VALID',
  qrToken: 'header.payload.signature',
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
  return router
}

beforeEach(() => {
  mockMe(meResponse(['CUSTOMER']))
  server.use(http.get('/api/v1/administrative-units', () => HttpResponse.json([])))
})

describe('Vé của tôi (AC-9)', () => {
  it('chia vé theo tab và mở chi tiết có mã QR', async () => {
    server.use(
      http.get('/api/v1/me/tickets', () =>
        HttpResponse.json([
          ticket('t1'),
          ticket('t2', { startsAt: past, status: 'USED', eventTitle: 'Indie Showcase' }),
          ticket('t3', { status: 'REVOKED', eventTitle: 'Lễ hội Đà Lạt' }),
        ]),
      ),
    )
    const user = userEvent.setup()
    renderAt('/me/tickets')

    expect(await screen.findByText('Đêm nhạc Rock Sài Gòn', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByText('Indie Showcase')).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Đã qua (1)' })).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Đã hoàn (1)' }))
    expect(screen.getByText('Lễ hội Đà Lạt')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Sắp diễn ra (1)' }))
    await user.click(screen.getByRole('link', { name: /Đêm nhạc Rock Sài Gòn/ }))
    expect(await screen.findByRole('img', { name: 'Mã QR của vé' })).toBeInTheDocument()
    expect(screen.getByText('Hợp lệ')).toBeInTheDocument()
  })

  it('chưa có vé: hiện trạng thái trống', async () => {
    server.use(http.get('/api/v1/me/tickets', () => HttpResponse.json([])))
    renderAt('/me/tickets')
    expect(await screen.findByText('Không có vé phù hợp.', undefined, { timeout: 5000 })).toBeInTheDocument()
  })
})

describe('Đơn hàng của tôi', () => {
  it('liệt kê đơn, chỉ đơn chờ thanh toán có nút tiếp tục thanh toán', async () => {
    server.use(
      http.get('/api/v1/me/bookings', () =>
        HttpResponse.json([
          {
            bookingId: '00000000-0000-7000-8000-0000000a0001',
            eventTitle: 'Đêm nhạc Rock Sài Gòn',
            startsAt: future,
            status: 'PENDING_PAYMENT',
            totalMinor: 5_000_000,
            currency: 'VND',
            createdAt: past,
          },
          {
            bookingId: '00000000-0000-7000-8000-0000000a0002',
            eventTitle: 'Hòa nhạc Mùa Thu',
            startsAt: future,
            status: 'CONFIRMED',
            totalMinor: 1_200_000,
            currency: 'VND',
            createdAt: past,
          },
        ]),
      ),
    )
    renderAt('/me/bookings')

    expect(await screen.findByText('Đêm nhạc Rock Sài Gòn', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('5.000.000 ₫')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Tiếp tục thanh toán' })).toHaveLength(1)
    expect(screen.getAllByRole('link', { name: 'Xem vé' })).toHaveLength(1)
  })
})
