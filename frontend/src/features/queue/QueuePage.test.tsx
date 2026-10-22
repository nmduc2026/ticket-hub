import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor, within } from '@testing-library/react'
import { act } from 'react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { FakeEventSource, installFakeEventSource } from '@/test/fake-event-source'
import { meResponse, mockMe, server } from '@/test/server'

const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'

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
  highDemand: true,
  tiers: [{ id: 't1', name: 'VIP', priceMinor: 2_500_000, currency: 'VND', available: 3 }],
}

function problem(status: number, code: string) {
  return HttpResponse.json(
    { type: 'about:blank', title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function mockQueue({
  join = { phase: 'QUEUED', position: 200, estimatedWaitSeconds: 10 },
  status = join,
  event = detail,
}: { join?: object; status?: object; event?: object } = {}) {
  mockMe(meResponse(['CUSTOMER']))
  const calls = { join: 0, status: 0 }
  server.use(
    http.get('/api/v1/administrative-units', () => HttpResponse.json([])),
    http.get(`/api/v1/events/${EVENT_ID}`, () => HttpResponse.json(event)),
    http.get(`/api/v1/events/${EVENT_ID}/seats`, () => HttpResponse.json({ eventId: EVENT_ID, eventStatus: 'ON_SALE', width: 100, height: 100, tiers: [], sections: [], elements: [], seats: [] })),
    http.post(`/api/v1/queue/${EVENT_ID}/join`, () => {
      calls.join++
      return HttpResponse.json(join, { status: 202 })
    }),
    http.get(`/api/v1/queue/${EVENT_ID}/status`, () => {
      calls.status++
      return HttpResponse.json(status)
    }),
  )
  return calls
}

function renderQueue() {
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [`/events/${EVENT_ID}/queue`] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

async function connected() {
  await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1))
  const source = FakeEventSource.latest()
  act(() => source.reconnect())
  return source
}

beforeEach(() => installFakeEventSource())
afterEach(() => vi.unstubAllGlobals())

describe('phòng chờ (màn #4, BR-04)', () => {
  it('vào hàng: hiện số người phía trước và thời gian chờ; SSE position cập nhật vị trí', async () => {
    mockQueue()
    renderQueue()

    // Test đầu tiên của file nạp route lần đầu nên chậm hơn.
    expect(await screen.findByRole('heading', { name: 'Đang trong hàng chờ' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('199')).toBeInTheDocument()
    expect(screen.getByText('dưới 1 phút')).toBeInTheDocument()
    expect(FakeEventSource.latest().url).toBe(`/api/v1/queue/${EVENT_ID}/stream`)

    const source = await connected()
    act(() => source.emit('position', { phase: 'QUEUED', position: 50, estimatedWaitSeconds: 130 }))

    expect(await screen.findByText('49')).toBeInTheDocument()
    expect(screen.getByText('khoảng 3 phút')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Tiến độ hàng chờ' })).toHaveAttribute('aria-valuenow', '75')
  })

  it('trước giờ mở bán: báo thứ tự được bốc thăm khi mở bán', async () => {
    mockQueue({ join: { phase: 'LOTTERY' } })
    renderQueue()

    expect(await screen.findByRole('heading', { name: 'Đã vào hàng chờ' })).toBeInTheDocument()
    expect(screen.getByText(/bốc thăm/)).toBeInTheDocument()
  })

  it('AC-4: được gọi tên → "Đến lượt bạn" và tự chuyển sang chọn ghế', async () => {
    mockQueue()
    const router = renderQueue()
    await screen.findByRole('heading', { name: 'Đang trong hàng chờ' })
    const source = await connected()

    act(() => source.emit('admitted', { admissionExpiresAt: new Date(Date.now() + 15 * 60_000).toISOString() }))

    expect(await screen.findByRole('heading', { name: 'Đến lượt bạn' })).toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveTextContent(/Còn 1[45]:\d\d/)
    await waitFor(() => expect(router.state.location.pathname).toBe(`/events/${EVENT_ID}/seats`), { timeout: 6000 })
    expect(source.readyState).toBe(FakeEventSource.CLOSED)
  }, 10_000)

  it('đã có lượt vào khi vào lại (F5): hiện "Đến lượt bạn" ngay, không mở luồng SSE', async () => {
    mockQueue({ join: { phase: 'ADMITTED', admissionExpiresAt: new Date(Date.now() + 600_000).toISOString() } })
    renderQueue()

    expect(await screen.findByRole('heading', { name: 'Đến lượt bạn' })).toBeInTheDocument()
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('AC-4: SSE đứt rồi nối lại → hỏi status rồi nghe tiếp, không mất lượt', async () => {
    const calls = mockQueue({
      join: { phase: 'QUEUED', position: 5, estimatedWaitSeconds: 1 },
      status: { phase: 'ADMITTED', admissionExpiresAt: new Date(Date.now() + 600_000).toISOString() },
    })
    renderQueue()
    await screen.findByRole('heading', { name: 'Đang trong hàng chờ' })
    const source = await connected()

    act(() => source.reconnect())

    expect(await screen.findByRole('heading', { name: 'Đến lượt bạn' })).toBeInTheDocument()
    expect(calls.status).toBe(1)
  })

  it('SSE bị từ chối → polling status mỗi 5 giây', async () => {
    const calls = mockQueue({
      join: { phase: 'QUEUED', position: 5, estimatedWaitSeconds: 1 },
      status: { phase: 'QUEUED', position: 2, estimatedWaitSeconds: 1 },
    })
    renderQueue()
    await screen.findByRole('heading', { name: 'Đang trong hàng chờ' })
    const source = await connected()
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })

    act(() => source.fail())
    await act(async () => {
      vi.advanceTimersByTime(5_000)
    })
    vi.useRealTimers()

    await waitFor(() => expect(calls.status).toBeGreaterThanOrEqual(1))
    expect(await screen.findByText('1')).toBeInTheDocument()
  })

  it('sự kiện bị tạm dừng: nhận closed → báo lý do, không còn vị trí', async () => {
    mockQueue()
    renderQueue()
    await screen.findByRole('heading', { name: 'Đang trong hàng chờ' })
    const source = await connected()

    act(() => source.emit('closed', { reason: 'SUSPENDED' }))

    expect(await screen.findByRole('heading', { name: 'Sự kiện tạm dừng bán' })).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('còn vé = 0: hiện "Hiện đã hết vé, ghế có thể trống lại"', async () => {
    mockQueue({ event: { ...detail, soldOut: true } })
    renderQueue()

    expect(await screen.findByText('Hiện đã hết vé, ghế có thể trống lại.')).toBeInTheDocument()
  })

  it.each([
    ['QUEUE_NOT_OPEN', 409, 'Phòng chờ chưa mở'],
    ['QUEUE_CLOSED', 409, 'Phòng chờ đã đóng'],
    ['QUEUE_NOT_ENABLED', 404, 'Sự kiện này không cần xếp hàng'],
    ['EVENT_NOT_FOUND', 404, 'Không tìm thấy sự kiện'],
  ])('join trả %s → hiện thông báo riêng', async (code, status, title) => {
    mockMe(meResponse(['CUSTOMER']))
    server.use(
      http.get('/api/v1/administrative-units', () => HttpResponse.json([])),
      http.get(`/api/v1/events/${EVENT_ID}`, () => HttpResponse.json(detail)),
      http.post(`/api/v1/queue/${EVENT_ID}/join`, () => problem(status, code)),
    )
    renderQueue()

    const dialogHeading = await screen.findByRole('heading', { name: title })
    expect(within(dialogHeading.closest('main') as HTMLElement).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('"Rời hàng chờ" về trang chi tiết sự kiện', async () => {
    mockQueue()
    renderQueue()

    expect(await screen.findByRole('link', { name: 'Rời hàng chờ' })).toHaveAttribute('href', `/events/${EVENT_ID}`)
  })
})

describe('nút mua trên trang chi tiết (màn #3)', () => {
  function renderDetail(event: object) {
    mockMe(meResponse(['CUSTOMER']))
    server.use(
      http.get('/api/v1/administrative-units', () => HttpResponse.json([])),
      http.get(`/api/v1/events/${EVENT_ID}`, () => HttpResponse.json(event)),
    )
    const queryClient = createQueryClient()
    const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [`/events/${EVENT_ID}`] }))
    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )
  }

  it('sự kiện có phòng chờ đang bán: "Vào phòng chờ" thay cho "Chọn ghế"', async () => {
    renderDetail(detail)

    expect(await screen.findByRole('link', { name: 'Vào phòng chờ' })).toHaveAttribute('href', `/events/${EVENT_ID}/queue`)
    expect(screen.queryByRole('link', { name: 'Chọn ghế' })).not.toBeInTheDocument()
  })

  it('sự kiện thường: vẫn "Chọn ghế"', async () => {
    renderDetail({ ...detail, highDemand: false })

    expect(await screen.findByRole('link', { name: 'Chọn ghế' })).toHaveAttribute('href', `/events/${EVENT_ID}/seats`)
    expect(screen.queryByRole('link', { name: 'Vào phòng chờ' })).not.toBeInTheDocument()
  })

  it('chưa tới giờ mở phòng chờ (còn hơn 15 phút): chỉ hiện giờ mở bán', async () => {
    renderDetail({ ...detail, status: 'PUBLISHED', saleStartsAt: new Date(Date.now() + 3_600_000).toISOString() })

    expect(await screen.findByText(/Mở bán lúc/)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Vào phòng chờ' })).not.toBeInTheDocument()
  })

  it('trong 15 phút trước giờ mở bán: hiện "Vào phòng chờ" kèm giờ mở bán', async () => {
    renderDetail({ ...detail, status: 'PUBLISHED', saleStartsAt: new Date(Date.now() + 10 * 60_000).toISOString() })

    expect(await screen.findByRole('link', { name: 'Vào phòng chờ' })).toBeInTheDocument()
    expect(screen.getByText(/Mở bán lúc/)).toBeInTheDocument()
  })
})
