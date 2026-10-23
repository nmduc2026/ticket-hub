import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const VENUE = { id: 'v1', name: 'Nhà hát Hòa Bình', address: '240 Ba Tháng Hai', wardName: 'Phường 12', cityName: 'TP.HCM', capacity: 500 }

function event(index: number, overrides: Record<string, unknown> = {}) {
  return {
    id: `e${index}`,
    title: `Sự kiện ${index}`,
    status: 'DRAFT',
    startsAt: '2026-11-15T13:00:00Z',
    venueId: 'v1',
    venueName: VENUE.name,
    ...overrides,
  }
}

function mockOrganizer(handler: (url: URL) => Response | Promise<Response>) {
  mockMe(meResponse(['CUSTOMER', 'ORGANIZER']))
  server.use(
    http.get('/api/v1/venues', () => HttpResponse.json([VENUE])),
    http.get('/api/v1/organizer/events', ({ request }) => handler(new URL(request.url))),
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

describe('danh sách sự kiện của organizer (#12)', () => {
  it('hiện bảng, và chọn dòng đổi "Xuất" thành "Xuất (n)"', async () => {
    mockOrganizer(() => HttpResponse.json({ items: [event(1), event(2)], total: 2, page: 0, size: 10 }))
    const user = userEvent.setup()
    renderAt('/organizer/events')

    expect(await screen.findByText('Sự kiện 1', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xuất' })).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: 'Chọn dòng Sự kiện 2' }))
    expect(screen.getByRole('button', { name: 'Xuất (1)' })).toBeInTheDocument()
    expect(screen.getByText('1 / 2 dòng được chọn.')).toBeInTheDocument()
  })

  it('bộ lọc trạng thái ghi lên URL, gửi lên API và "Đặt lại" xoá hết', async () => {
    const requests: URL[] = []
    mockOrganizer((url) => {
      requests.push(url)
      return HttpResponse.json({ items: [event(1)], total: 1, page: 0, size: 10 })
    })
    const user = userEvent.setup()
    const router = renderAt('/organizer/events')
    await screen.findByText('Sự kiện 1', undefined, { timeout: 5000 })

    await user.click(screen.getByRole('button', { name: /Trạng thái/ }))
    await user.click(screen.getByRole('checkbox', { name: 'Đang mở bán' }))

    await waitFor(() => expect(router.state.location.search).toMatchObject({ status: ['ON_SALE'] }))
    await waitFor(() => expect(requests.at(-1)?.searchParams.getAll('status')).toEqual(['ON_SALE']))

    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: 'Đặt lại' }))
    await waitFor(() => expect(router.state.location.search).toEqual({}))
  })

  it('đọc bộ lọc từ URL khi tải lại trang', async () => {
    const requests: URL[] = []
    mockOrganizer((url) => {
      requests.push(url)
      return HttpResponse.json({ items: [], total: 0, page: 0, size: 10 })
    })
    renderAt('/organizer/events?q=jazz&status=DRAFT&status=ENDED')

    expect(await screen.findByText('Không có kết quả khớp bộ lọc', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toHaveValue('jazz')
    expect(requests[0].searchParams.get('q')).toBe('jazz')
    expect(requests[0].searchParams.getAll('status')).toEqual(['DRAFT', 'ENDED'])
  })

  it('trống khi chưa có sự kiện và không có bộ lọc', async () => {
    mockOrganizer(() => HttpResponse.json({ items: [], total: 0, page: 0, size: 10 }))
    renderAt('/organizer/events')
    expect(await screen.findByText('Chưa có sự kiện', undefined, { timeout: 5000 })).toBeInTheDocument()
  })

  it('lỗi máy chủ hiện mã tra cứu và nút Thử lại gọi lại API', async () => {
    let calls = 0
    mockOrganizer(() => {
      calls += 1
      return calls <= 3
        ? HttpResponse.json(
            { status: 503, code: 'UNAVAILABLE', traceId: '7f3a-91c2' },
            { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
          )
        : HttpResponse.json({ items: [event(1)], total: 1, page: 0, size: 10 })
    })
    const user = userEvent.setup()
    renderAt('/organizer/events')

    const error = await screen.findByText('Không tải được dữ liệu', undefined, { timeout: 10_000 })
    expect(within(error.closest('[role="status"]') as HTMLElement).getByText(/7f3a-91c2/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Sự kiện 1')).toBeInTheDocument()
  }, 20_000)

  it('đang tải hiện khung xương, không hiện dòng dữ liệu', async () => {
    mockOrganizer(() => new Promise<Response>(() => {}))
    renderAt('/organizer/events')
    expect(await screen.findByRole('heading', { name: 'Sự kiện' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByText('Chưa có sự kiện')).not.toBeInTheDocument()
    expect(screen.queryByText('Không tải được dữ liệu')).not.toBeInTheDocument()
  })
})
