import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

function account(index: number, overrides: Record<string, unknown> = {}) {
  return {
    userId: `u${index}`,
    email: `user${index}@example.test`,
    fullName: `Người dùng ${index}`,
    roles: ['CUSTOMER'],
    enabled: true,
    lastLoginAt: '2026-10-20T03:00:00Z',
    ...overrides,
  }
}

function page(items: unknown[]) {
  return HttpResponse.json({ items, total: items.length, page: 0, size: 10 })
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

describe('người dùng của Admin (#28)', () => {
  beforeEach(() => mockMe(meResponse(['CUSTOMER', 'ADMIN'])))

  it('tìm và lọc vai trò gửi lên API qua URL', async () => {
    const requests: URL[] = []
    server.use(
      http.get('/api/v1/admin/users', ({ request }) => {
        requests.push(new URL(request.url))
        return page([account(1)])
      }),
    )
    renderAt('/admin/users?q=lan&role=ORGANIZER')

    expect(await screen.findByText('Người dùng 1', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(requests[0].searchParams.get('q')).toBe('lan')
    expect(requests[0].searchParams.get('role')).toBe('ORGANIZER')
    expect(screen.getByRole('searchbox')).toHaveValue('lan')
  })

  it('khoá phải qua hộp xác nhận; huỷ thì không gọi API', async () => {
    let locked = 0
    server.use(
      http.get('/api/v1/admin/users', () => page([account(1)])),
      http.post('/api/v1/admin/users/u1/lock', () => {
        locked += 1
        return HttpResponse.json({ enabled: false })
      }),
    )
    const user = userEvent.setup()
    renderAt('/admin/users')
    await screen.findByText('Người dùng 1', undefined, { timeout: 5000 })

    await user.click(screen.getByRole('button', { name: /Người dùng 1/ }))
    await user.click(await screen.findByRole('menuitem', { name: 'Khoá tài khoản' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Khoá tài khoản Người dùng 1\?/)).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Huỷ' }))
    expect(locked).toBe(0)

    await user.click(screen.getByRole('button', { name: /Người dùng 1/ }))
    await user.click(await screen.findByRole('menuitem', { name: 'Khoá tài khoản' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Khoá tài khoản' }))

    await waitFor(() => expect(locked).toBe(1))
    expect(await screen.findByText('Đã khoá tài khoản')).toBeInTheDocument()
  })

  it('tài khoản đã khoá có thể mở khoá', async () => {
    let unlocked = 0
    server.use(
      http.get('/api/v1/admin/users', () => page([account(2, { enabled: false })])),
      http.post('/api/v1/admin/users/u2/unlock', () => {
        unlocked += 1
        return HttpResponse.json({ enabled: true })
      }),
    )
    const user = userEvent.setup()
    renderAt('/admin/users')
    expect(await screen.findByText('Đã khoá', undefined, { timeout: 5000 })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Người dùng 2/ }))
    await user.click(await screen.findByRole('menuitem', { name: 'Mở khoá' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Mở khoá' }))

    await waitFor(() => expect(unlocked).toBe(1))
  })

  it('khoá chính mình bị từ chối thì báo lỗi bằng tiếng Việt', async () => {
    server.use(
      http.get('/api/v1/admin/users', () => page([account(3)])),
      http.post('/api/v1/admin/users/u3/lock', () =>
        HttpResponse.json({ status: 422, code: 'CANNOT_LOCK_SELF' }, { status: 422, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    )
    const user = userEvent.setup()
    renderAt('/admin/users')
    await screen.findByText('Người dùng 3', undefined, { timeout: 5000 })

    await user.click(screen.getByRole('button', { name: /Người dùng 3/ }))
    await user.click(await screen.findByRole('menuitem', { name: 'Khoá tài khoản' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Khoá tài khoản' }))

    expect(await screen.findByText('Không thể khoá chính tài khoản của bạn.')).toBeInTheDocument()
  })

  it('chi tiết người dùng hiện đơn, vé, hoàn tiền; organizer có thêm sự kiện', async () => {
    server.use(
      http.get('/api/v1/admin/users', () => page([account(4, { roles: ['CUSTOMER', 'ORGANIZER'] })])),
      http.get('/api/v1/admin/users/u4', () =>
        HttpResponse.json({
          profile: account(4, { roles: ['CUSTOMER', 'ORGANIZER'] }),
          bookings: [
            {
              bookingId: 'b1',
              eventTitle: 'Đêm nhạc Indie',
              startsAt: '2026-11-15T13:00:00Z',
              status: 'CONFIRMED',
              totalMinor: 1_900_000,
              currency: 'VND',
              createdAt: '2026-10-20T03:00:00Z',
              ticketCount: 2,
            },
          ],
          tickets: [],
          refunds: [],
          events: [{ eventId: 'e1', title: 'Liveshow của tôi', status: 'ON_SALE', startsAt: '2026-12-01T13:00:00Z' }],
        }),
      ),
    )
    renderAt('/admin/users?view=u4')

    const sheet = await screen.findByRole('dialog', undefined, { timeout: 5000 })
    expect(await within(sheet).findByText('Đêm nhạc Indie')).toBeInTheDocument()
    expect(within(sheet).getByText('Liveshow của tôi')).toBeInTheDocument()
    expect(within(sheet).getAllByText('Chưa có')).toHaveLength(2)
  })
})
