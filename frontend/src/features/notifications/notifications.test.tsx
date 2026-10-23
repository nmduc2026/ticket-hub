import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import type { Notification } from '@/api/generated/model'
import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

import { groupNotifications } from './notificationGroups'

const hour = 3_600_000
const at = (hoursAgo: number) => new Date(Date.now() - hoursAgo * hour).toISOString()

const notification = (id: string, overrides: Partial<Notification> = {}): Notification => ({
  id,
  type: 'TICKET_ISSUED',
  title: 'Vé của bạn đã sẵn sàng',
  body: 'Hòa nhạc Mùa Thu: 2 vé.',
  link: '/me/tickets',
  createdAt: at(1),
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

/** Hộp thư giả có trạng thái: đọc một cái hoặc tất cả thì số chưa đọc giảm theo. */
function mockInbox(initial: Notification[]) {
  const items = initial.map((item) => ({ ...item }))
  const calls = { read: [] as string[], readAll: 0 }
  const unread = () => items.filter((item) => !item.readAt).length
  server.use(
    http.get('/api/v1/me/notifications/unread-count', () => HttpResponse.json({ count: unread() })),
    http.get('/api/v1/me/notifications', ({ request }) => {
      const onlyUnread = new URL(request.url).searchParams.get('unread') === 'true'
      return HttpResponse.json({ items: items.filter((item) => !onlyUnread || !item.readAt) })
    }),
    http.post('/api/v1/me/notifications/read-all', () => {
      calls.readAll++
      items.forEach((item) => (item.readAt ??= new Date().toISOString()))
      return new HttpResponse(null, { status: 204 })
    }),
    http.post('/api/v1/me/notifications/:id/read', ({ params }) => {
      calls.read.push(String(params.id))
      const found = items.find((item) => item.id === params.id)
      if (found) found.readAt ??= new Date().toISOString()
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return calls
}

beforeEach(() => {
  mockMe(meResponse(['CUSTOMER']))
  server.use(http.get('/api/v1/administrative-units', () => HttpResponse.json([])))
})

describe('Chuông thông báo (AC-15)', () => {
  it('hiện số chưa đọc; mở popover; đánh dấu một thông báo rồi tất cả thì số về 0', async () => {
    const calls = mockInbox([notification('n1'), notification('n2', { title: 'Đã hoàn tiền', type: 'REFUND_COMPLETED' })])
    const user = userEvent.setup()
    renderAt('/me/tickets')
    server.use(http.get('/api/v1/me/tickets', () => HttpResponse.json([])))

    const bell = await screen.findByRole('button', { name: 'Thông báo (2 chưa đọc)' }, { timeout: 5000 })
    expect(within(bell).getByTestId('bell-count')).toHaveTextContent('2')

    await user.click(bell)
    const popover = await screen.findByText('Đã hoàn tiền', undefined, { timeout: 5000 })
    expect(popover).toBeInTheDocument()

    await user.click(screen.getByText('Vé của bạn đã sẵn sàng'))
    await waitFor(() => expect(calls.read).toEqual(['n1']))
    expect(await screen.findByRole('button', { name: 'Thông báo (1 chưa đọc)' }, { timeout: 5000 })).toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: 'Thông báo (1 chưa đọc)' }))
    await user.click(await screen.findByRole('button', { name: 'Đánh dấu tất cả đã đọc' }))
    await waitFor(() => expect(calls.readAll).toBe(1))
    expect(await screen.findByRole('button', { name: 'Thông báo' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByTestId('bell-count')).not.toBeInTheDocument()
  }, 20_000)

  it('khách chưa đăng nhập không có chuông và không gọi API thông báo', async () => {
    mockMe(null)
    let called = false
    server.use(
      http.get('/api/v1/me/notifications/unread-count', () => {
        called = true
        return HttpResponse.json({ count: 0 })
      }),
      http.get('/api/v1/events', () => HttpResponse.json({ items: [], total: 0 })),
    )
    renderAt('/events')

    expect(await screen.findByRole('button', { name: 'Đăng nhập' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Thông báo/ })).not.toBeInTheDocument()
    expect(called).toBe(false)
  })
})

describe('Trang Thông báo', () => {
  it('nhóm theo độ mới, lọc Chưa đọc, "Đánh dấu tất cả đã đọc" làm hết thông báo thành đã đọc', async () => {
    const calls = mockInbox([
      notification('n1', { createdAt: at(0.1), title: 'Mới nhất' }),
      notification('n2', { createdAt: at(24 * 3), title: 'Ba ngày trước', readAt: at(1) }),
      notification('n3', { createdAt: at(24 * 30), title: 'Tháng trước' }),
    ])
    const user = userEvent.setup()
    renderAt('/me/notifications')

    expect(await screen.findByText('Mới nhất', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '7 ngày qua' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Trước đó' })).toBeInTheDocument()
    expect(screen.getByText('Thông báo được lưu 90 ngày.')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Chưa đọc' }))
    await waitFor(() => expect(screen.queryByText('Ba ngày trước')).not.toBeInTheDocument())
    expect(screen.getByText('Tháng trước')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' }))
    await waitFor(() => expect(calls.readAll).toBe(1))
    expect(await screen.findByText('Không còn thông báo chưa đọc', undefined, { timeout: 5000 })).toBeInTheDocument()
  })

  it('chưa có thông báo: hiện trạng thái trống', async () => {
    mockInbox([])
    renderAt('/me/notifications')

    expect(await screen.findByText('Chưa có thông báo', undefined, { timeout: 5000 })).toBeInTheDocument()
  })
})

describe('Cài đặt thông báo (AC-15)', () => {
  const preference = (type: string, label: string, mandatory: boolean, email = true, inApp = true) => ({
    type,
    label,
    mandatory,
    email,
    inApp,
  })

  it('loại bắt buộc không tắt được; chỉ gửi lên loại đã đổi', async () => {
    let sent: unknown
    server.use(
      http.get('/api/v1/me/notification-preferences', () =>
        HttpResponse.json([
          preference('TICKET_ISSUED', 'Vé đã phát', true),
          preference('SALE_PAUSED', 'Tạm dừng bán', false),
          preference('HOLD_EXPIRING', 'Sắp hết giờ giữ ghế', false),
        ]),
      ),
      http.put('/api/v1/me/notification-preferences', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json([])
      }),
      http.get('/api/v1/me/profile', () => HttpResponse.json({ userId: 'u1', email: 'customer@tickethub.local' })),
      http.get('/api/v1/administrative-units', () => HttpResponse.json([])),
    )
    const user = userEvent.setup()
    renderAt('/me/profile')

    await user.click(await screen.findByRole('tab', { name: 'Thông báo' }, { timeout: 5000 }))
    expect(await screen.findByRole('checkbox', { name: 'Vé đã phát · Email' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Vé đã phát · Trong app' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Lưu cài đặt' })).toBeDisabled()

    await user.click(screen.getByRole('checkbox', { name: 'Tạm dừng bán · Trong app' }))
    await user.click(screen.getByRole('button', { name: 'Lưu cài đặt' }))

    await waitFor(() =>
      expect(sent).toEqual({ preferences: [{ type: 'SALE_PAUSED', email: true, inApp: false }] }),
    )
  })
})

describe('groupNotifications', () => {
  it('chia hôm nay / 7 ngày qua / cũ hơn theo ngày Việt Nam, bỏ nhóm rỗng, giữ thứ tự', () => {
    const now = new Date('2026-11-10T05:00:00Z') // 12:00 giờ Việt Nam
    const groups = groupNotifications(
      [
        notification('a', { createdAt: '2026-11-10T04:00:00Z' }),
        notification('b', { createdAt: '2026-11-09T16:59:00Z' }), // 23:59 hôm qua giờ VN: không phải hôm nay
        notification('c', { createdAt: '2026-10-01T00:00:00Z' }),
      ],
      now,
    )

    expect(groups.map((entry) => [entry.group, entry.items.map((item) => item.id)])).toEqual([
      ['today', ['a']],
      ['week', ['b']],
      ['older', ['c']],
    ])
  })

  it('23:30 UTC hôm qua đã là sáng nay giờ Việt Nam nên thuộc "Hôm nay"', () => {
    const now = new Date('2026-11-10T05:00:00Z')

    expect(groupNotifications([notification('x', { createdAt: '2026-11-09T18:00:00Z' })], now)[0]?.group).toBe('today')
  })
})
