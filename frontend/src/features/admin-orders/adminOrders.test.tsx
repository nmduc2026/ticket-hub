import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const BOOKING_ID = '0190aaaa-0000-7000-8000-00000000abcd'

function renderAt(path: string) {
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [path] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe('đơn hàng & đối soát của Admin (#29)', () => {
  beforeEach(() => mockMe(meResponse(['CUSTOMER', 'ADMIN'])))

  it('mở side sheet thanh toán: số tiền, lần hoàn lỗi và dòng thời gian Stripe theo thứ tự', async () => {
    server.use(
      http.get('/api/v1/admin/bookings', () =>
        HttpResponse.json({
          items: [
            {
              bookingId: BOOKING_ID,
              eventTitle: 'Đêm nhạc indie',
              organizerName: 'Ban tổ chức 1',
              contactEmail: 'khach@example.test',
              status: 'REFUND_FAILED',
              needsReconcile: false,
              totalMinor: 1_000_000,
              currency: 'VND',
              createdAt: '2026-10-21T03:00:00Z',
            },
          ],
          total: 1,
          page: 0,
          size: 20,
        }),
      ),
      http.get(`/api/v1/admin/bookings/${BOOKING_ID}/payment`, () =>
        HttpResponse.json({
          status: 'SUCCEEDED',
          amountMinor: 1_000_000,
          applicationFeeMinor: 50_000,
          currency: 'VND',
          stripePaymentIntentId: 'pi_test_123',
          destinationAccountId: 'acct_dest',
          refunds: [{ reason: 'CUSTOMER_REQUEST', status: 'FAILED', amountMinor: 950_000, failureReason: 'card_declined', createdAt: '2026-10-21T03:05:00Z' }],
          timeline: [
            { at: '2026-10-21T03:00:00Z', kind: 'PAYMENT_CREATED', label: 'pi_test_123', unprocessed: false },
            { at: '2026-10-21T03:01:00Z', kind: 'STRIPE_EVENT', label: 'payment_intent.succeeded', unprocessed: false },
            { at: '2026-10-21T03:06:00Z', kind: 'STRIPE_EVENT', label: 'charge.refunded', unprocessed: true },
          ],
        }),
      ),
    )
    const user = userEvent.setup()
    renderAt('/admin/orders')
    await screen.findByText('Đêm nhạc indie', undefined, { timeout: 5000 })

    await user.click(screen.getByRole('button', { name: 'Mở chi tiết đơn' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Xem thanh toán' }))

    expect(await screen.findByText('acct_dest')).toBeInTheDocument()
    expect(screen.getByText('card_declined')).toBeInTheDocument()
    const steps = screen
      .getAllByRole('listitem')
      .map((item) => item.textContent ?? '')
      .filter((text) => /Tạo thanh toán|Event Stripe/.test(text))
    expect(steps).toHaveLength(3)
    expect(steps[0]).toContain('Tạo thanh toán')
    expect(steps[1]).toContain('payment_intent.succeeded')
    expect(steps[2]).toContain('charge.refunded')
    expect(steps[2]).toContain('Chưa xử lý')
  })
})
