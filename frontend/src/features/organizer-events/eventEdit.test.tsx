import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'
const VENUE = { id: 'v1', name: 'Nhà hát Hòa Bình', address: '240 Ba Tháng Hai', wardName: 'Phường 12', cityName: 'TP.HCM', capacity: 500 }
const SEAT_MAP = {
  id: 'm1',
  name: 'Sơ đồ chính',
  version: 1,
  width: 1200,
  height: 800,
  locked: false,
  sections: [
    { id: 'sa', code: 'A', name: 'Khu A', seatCount: 160, shape: { kind: 'GRID', x: 60, y: 160, rows: 8, seatsPerRow: 20, rowGap: 26, seatGap: 24 } },
    { id: 'sb', code: 'B', name: 'Khu B', seatCount: 200, shape: { kind: 'GRID', x: 640, y: 160, rows: 10, seatsPerRow: 20, rowGap: 26, seatGap: 24 } },
    { id: 'sc', code: 'C', name: 'Khu C', seatCount: 140, shape: { kind: 'GRID', x: 350, y: 480, rows: 7, seatsPerRow: 20, rowGap: 26, seatGap: 24 } },
  ],
  elements: [{ id: 'e1', type: 'STAGE', label: 'Sân khấu', shape: { x: 350, y: 40, w: 500, h: 60, rotation: 0 } }],
}

type Detail = Record<string, unknown>

function eventDetail(overrides: Detail = {}): Detail {
  return {
    id: EVENT_ID,
    title: 'Đêm nhạc Rock Sài Gòn',
    description: 'Mô tả sự kiện',
    category: 'Nhạc sống',
    status: 'DRAFT',
    venueId: VENUE.id,
    venueName: VENUE.name,
    seatMapId: SEAT_MAP.id,
    startsAt: '2026-11-15T13:00:00Z',
    endsAt: '2026-11-15T15:30:00Z',
    saleStartsAt: '2026-10-01T03:00:00Z',
    maxTicketsPerUser: 6,
    inventoryReady: false,
    salesInfoLocked: false,
    tiers: [],
    tierSections: [],
    ...overrides,
  }
}

function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { type: 'about:blank', title: code, status, code, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

/** Dựng API giả cho một sự kiện; `state.detail` đổi được giữa các lần gọi để mô phỏng server. */
function mockOrganizerApi(initial: Detail) {
  const state = { detail: initial }
  mockMe(meResponse(['CUSTOMER', 'ORGANIZER']))
  server.use(
    http.get('/api/v1/venues', () => HttpResponse.json([VENUE])),
    http.get(`/api/v1/venues/${VENUE.id}/seat-maps`, () => HttpResponse.json([SEAT_MAP])),
    http.get(`/api/v1/organizer/events/${EVENT_ID}`, () => HttpResponse.json(state.detail)),
  )
  return state
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

const T = { timeout: 5000 }

describe('khung sửa sự kiện', () => {
  it('sự kiện của organizer khác (404) chỉ hiện "Không tìm thấy sự kiện" (AC-4)', async () => {
    mockMe(meResponse(['CUSTOMER', 'ORGANIZER']))
    server.use(http.get(`/api/v1/organizer/events/${EVENT_ID}`, () => problem(404, 'EVENT_NOT_FOUND')))
    renderAt(`/organizer/events/${EVENT_ID}/edit`)

    expect(await screen.findByRole('heading', { name: 'Không tìm thấy sự kiện' }, T)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument()
  })

  it('đường dẫn gốc của sự kiện chuyển sang tab Thông tin chung', async () => {
    mockOrganizerApi(eventDetail())
    const router = renderAt(`/organizer/events/${EVENT_ID}`)
    expect(await screen.findByRole('heading', { name: 'Thông tin cơ bản' }, T)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/organizer/events/${EVENT_ID}/edit`)
  })

  it('bản nháp thiếu mục thì nút Publish liệt kê đúng các mục còn thiếu, không gọi server (BR-05)', async () => {
    mockOrganizerApi(eventDetail())
    let publishCalls = 0
    server.use(
      http.post(`/api/v1/organizer/events/${EVENT_ID}/publish`, () => {
        publishCalls += 1
        return HttpResponse.json(eventDetail())
      }),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/edit`)
    await user.click(await screen.findByRole('button', { name: 'Publish' }, T))

    const dialog = await screen.findByRole('dialog', { name: 'Chưa thể publish' })
    expect(within(dialog).getByText('Chưa có hạng vé')).toBeInTheDocument()
    expect(within(dialog).getByText('Chưa gán hạng vé cho khu nào')).toBeInTheDocument()
    expect(within(dialog).queryByText('Chưa chọn địa điểm')).not.toBeInTheDocument()
    expect(publishCalls).toBe(0)
  })

  it('đủ điều kiện: xác nhận rồi publish; server từ chối vì BR-05 thì hiện lý do', async () => {
    const full = eventDetail({
      tiers: [{ id: 't1', name: 'VIP', priceMinor: 2_500_000, currency: 'VND' }],
      tierSections: [{ sectionId: 'sb', tierId: 't1' }],
    })
    mockOrganizerApi(full)
    server.use(
      http.post(`/api/v1/organizer/events/${EVENT_ID}/publish`, () =>
        problem(409, 'PUBLISH_NOT_ALLOWED', { reasons: ['ORGANIZER_NOT_APPROVED', 'PAYOUT_NOT_READY'] }),
      ),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/edit`)
    await user.click(await screen.findByRole('button', { name: 'Publish' }, T))

    const confirm = await screen.findByRole('dialog', { name: /Publish “Đêm nhạc Rock Sài Gòn”\?/ })
    expect(within(confirm).getByText('200 ghế · 1 hạng vé')).toBeInTheDocument()
    await user.click(within(confirm).getByRole('button', { name: 'Publish' }))

    const blocked = await screen.findByRole('dialog', { name: 'Chưa thể publish' })
    expect(within(blocked).getByText('Hồ sơ ban tổ chức chưa được duyệt')).toBeInTheDocument()
    expect(within(blocked).getByText('Stripe chưa bật nhận thanh toán')).toBeInTheDocument()
  })

  it('publish thành công thì tải lại chi tiết và nút Publish biến mất', async () => {
    const state = mockOrganizerApi(
      eventDetail({
        tiers: [{ id: 't1', name: 'VIP', priceMinor: 2_500_000, currency: 'VND' }],
        tierSections: [{ sectionId: 'sb', tierId: 't1' }],
      }),
    )
    server.use(
      http.post(`/api/v1/organizer/events/${EVENT_ID}/publish`, () => {
        state.detail = { ...state.detail, status: 'PUBLISHED', salesInfoLocked: true, platformFeeBps: 500 }
        return HttpResponse.json(state.detail)
      }),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/edit`)
    await user.click(await screen.findByRole('button', { name: 'Publish' }, T))
    const confirm = await screen.findByRole('dialog', { name: /Publish/ })
    await user.click(within(confirm).getByRole('button', { name: 'Publish' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument(), T)
    expect(screen.getByText('Sắp mở bán')).toBeInTheDocument()
  })
})

describe('tab Thông tin chung (BR-14)', () => {
  it('sau publish: giờ, địa điểm, sơ đồ khoá; tiêu đề lưu được và gửi nguyên các trường đã khoá', async () => {
    const published = eventDetail({ status: 'PUBLISHED', salesInfoLocked: true })
    const state = mockOrganizerApi(published)
    let body: Record<string, unknown> = {}
    server.use(
      http.put(`/api/v1/organizer/events/${EVENT_ID}`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        state.detail = { ...state.detail, title: body.title }
        return HttpResponse.json(state.detail)
      }),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/edit`)

    const title = await screen.findByLabelText(/Tên sự kiện/, undefined, T)
    expect(screen.getByLabelText(/Ngày diễn/)).toBeDisabled()
    expect(screen.getByLabelText(/Giờ bắt đầu/)).toBeDisabled()
    expect(screen.getByRole('combobox', { name: /Địa điểm/ })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: /Sơ đồ ghế/ })).toBeDisabled()
    expect(title).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument()

    await user.clear(title)
    await user.type(title, 'Đêm nhạc Rock — đêm 2')
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    await waitFor(() => expect(body.title).toBe('Đêm nhạc Rock — đêm 2'))
    expect(body).toMatchObject({
      venueId: VENUE.id,
      seatMapId: SEAT_MAP.id,
      startsAt: '2026-11-15T13:00:00Z',
      endsAt: '2026-11-15T15:30:00Z',
      saleStartsAt: '2026-10-01T03:00:00Z',
      maxTicketsPerUser: 6,
    })
  })

  it('bản nháp: sửa ngày giờ theo giờ Việt Nam và gửi lên ISO UTC', async () => {
    mockOrganizerApi(eventDetail())
    let body: Record<string, unknown> = {}
    server.use(
      http.put(`/api/v1/organizer/events/${EVENT_ID}`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(eventDetail(body))
      }),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/edit`)
    const start = await screen.findByLabelText(/Giờ bắt đầu/, undefined, T)
    expect(start).toHaveValue('20:00')

    await user.clear(start)
    await user.type(start, '19:30')
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    await waitFor(() => expect(body.startsAt).toBe('2026-11-15T12:30:00.000Z'))
    expect(body.endsAt).toBe('2026-11-15T15:30:00.000Z')
  })

  it('EVENT_LOCKED từ server hiện thành thông báo trong trang', async () => {
    mockOrganizerApi(eventDetail())
    server.use(http.put(`/api/v1/organizer/events/${EVENT_ID}`, () => problem(409, 'EVENT_LOCKED')))
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/edit`)
    const title = await screen.findByLabelText(/Tên sự kiện/, undefined, T)
    await user.type(title, ' 2')
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    expect(await screen.findByText(/Sự kiện đã publish: giờ diễn/)).toBeInTheDocument()
  })

  it('ảnh sai loại hoặc quá 2 MB bị chặn ở client, không gửi lên server', async () => {
    mockOrganizerApi(eventDetail())
    let uploads = 0
    server.use(
      http.post(`/api/v1/organizer/events/${EVENT_ID}/banner`, () => {
        uploads += 1
        return HttpResponse.json(eventDetail())
      }),
    )
    const user = userEvent.setup({ applyAccept: false })
    renderAt(`/organizer/events/${EVENT_ID}/edit`)
    await screen.findByRole('heading', { name: 'Ảnh bìa' }, T)
    const input = document.querySelector<HTMLInputElement>('#banner-file')
    expect(input).not.toBeNull()

    await user.upload(input as HTMLInputElement, new File(['x'], 'a.gif', { type: 'image/gif' }))
    expect(await screen.findByText('Chỉ nhận ảnh jpg, png hoặc webp.')).toBeInTheDocument()

    await user.upload(input as HTMLInputElement, new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' }))
    expect(await screen.findByText('Ảnh tối đa 2 MB.')).toBeInTheDocument()
    expect(uploads).toBe(0)
  })

  it('ảnh hợp lệ được gửi bằng multipart', async () => {
    mockOrganizerApi(eventDetail())
    let contentType: string | null = null
    let hasFilePart = false
    server.use(
      http.post(`/api/v1/organizer/events/${EVENT_ID}/banner`, async ({ request }) => {
        contentType = request.headers.get('Content-Type')
        hasFilePart = (await request.formData()).has('file')
        return HttpResponse.json(eventDetail({ bannerUrl: 'http://s3/banner.png' }))
      }),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/edit`)
    await screen.findByRole('heading', { name: 'Ảnh bìa' }, T)
    await user.upload(document.querySelector('#banner-file') as HTMLInputElement, new File(['png-bytes'], 'banner.png', { type: 'image/png' }))

    // Nội dung nhị phân của file kiểm bằng Playwright trên trình duyệt thật (jsdom và Node không chung kiểu File).
    await waitFor(() => expect(hasFilePart).toBe(true))
    expect(contentType).toContain('multipart/form-data; boundary=')
  })
})

describe('tab Hạng vé & giá', () => {
  it('thêm hạng vé: tạo hạng vé rồi gán khu, giữ nguyên khu đã gán cho hạng khác', async () => {
    const state = mockOrganizerApi(
      eventDetail({
        tiers: [{ id: 't1', name: 'Thường', priceMinor: 800_000, currency: 'VND' }],
        tierSections: [{ sectionId: 'sa', tierId: 't1' }],
      }),
    )
    let created: Record<string, unknown> = {}
    let assigned: { assignments: { sectionId: string; tierId: string }[] } = { assignments: [] }
    server.use(
      http.post(`/api/v1/organizer/events/${EVENT_ID}/ticket-tiers`, async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 't2', name: created.name, priceMinor: created.priceMinor, currency: 'VND' }, { status: 201 })
      }),
      http.put(`/api/v1/organizer/events/${EVENT_ID}/tier-sections`, async ({ request }) => {
        assigned = (await request.json()) as typeof assigned
        state.detail = { ...state.detail, tierSections: assigned.assignments }
        return HttpResponse.json(assigned.assignments)
      }),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/tiers`)
    await user.click(await screen.findByRole('button', { name: 'Thêm hạng vé' }, T))

    const dialog = await screen.findByRole('dialog', { name: 'Thêm hạng vé' })
    // Khu A đã gán cho "Thường": hiện rõ và không chọn được.
    expect(within(dialog).getByRole('checkbox', { name: /Khu A/ })).toBeDisabled()
    expect(within(dialog).getByText('Đã gán: Thường')).toBeInTheDocument()

    await user.type(within(dialog).getByLabelText(/Tên hạng vé/), 'VIP')
    await user.type(within(dialog).getByLabelText(/^Giá/), '2500000')
    await user.click(within(dialog).getByRole('checkbox', { name: /Khu B/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hạng vé' }))

    await waitFor(() => expect(assigned.assignments).toHaveLength(2))
    expect(created).toEqual({ name: 'VIP', priceMinor: 2_500_000 })
    expect(assigned.assignments).toEqual(
      expect.arrayContaining([
        { sectionId: 'sa', tierId: 't1' },
        { sectionId: 'sb', tierId: 't2' },
      ]),
    )
  })

  it('đã publish: hạng vé chỉ đọc, không có nút thêm / sửa / xoá', async () => {
    mockOrganizerApi(
      eventDetail({
        status: 'PUBLISHED',
        salesInfoLocked: true,
        tiers: [{ id: 't1', name: 'Thường', priceMinor: 800_000, currency: 'VND' }],
        tierSections: [{ sectionId: 'sa', tierId: 't1' }],
      }),
    )
    renderAt(`/organizer/events/${EVENT_ID}/tiers`)

    expect(await screen.findByText('Thường', undefined, T)).toBeInTheDocument()
    expect(screen.getByText('800.000 ₫')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Thêm hạng vé' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Thao tác với hạng vé/ })).not.toBeInTheDocument()
  })

  it('sơ đồ xem trước tô khu đã gán và để xám khu chưa gán', async () => {
    mockOrganizerApi(
      eventDetail({
        tiers: [{ id: 't1', name: 'Thường', priceMinor: 800_000, currency: 'VND' }],
        tierSections: [{ sectionId: 'sa', tierId: 't1' }],
      }),
    )
    renderAt(`/organizer/events/${EVENT_ID}/tiers`)

    await waitFor(() => expect(screen.getByTestId('section-A')).toHaveAttribute('data-assigned', 'true'), T)
    expect(screen.getByTestId('section-B')).toHaveAttribute('data-assigned', 'false')
  })

  it('xoá hạng vé gọi DELETE và tải lại', async () => {
    const state = mockOrganizerApi(
      eventDetail({ tiers: [{ id: 't1', name: 'Thường', priceMinor: 800_000, currency: 'VND' }], tierSections: [] }),
    )
    let deleted = false
    server.use(
      http.delete(`/api/v1/organizer/events/${EVENT_ID}/ticket-tiers/t1`, () => {
        deleted = true
        state.detail = { ...state.detail, tiers: [] }
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    renderAt(`/organizer/events/${EVENT_ID}/tiers`)
    await user.click(await screen.findByRole('button', { name: 'Thao tác với hạng vé Thường' }, T))
    await user.click(await screen.findByRole('menuitem', { name: 'Xoá hạng vé' }))
    await user.click(within(await screen.findByRole('dialog', { name: /Xoá hạng vé “Thường”\?/ })).getByRole('button', { name: 'Xoá' }))

    await waitFor(() => expect(deleted).toBe(true))
    expect(await within(screen.getByRole('table')).findByText('Chưa có hạng vé')).toBeInTheDocument()
  })
})

describe('tạo sự kiện (modal #12)', () => {
  it('nhập tối thiểu, tạo rồi chuyển sang trang sửa; giờ gửi lên là UTC', async () => {
    mockMe(meResponse(['CUSTOMER', 'ORGANIZER']))
    let body: Record<string, unknown> = {}
    server.use(
      http.get('/api/v1/venues', () => HttpResponse.json([VENUE])),
      http.get('/api/v1/organizer/events', () => HttpResponse.json({ items: [], total: 0, page: 0, size: 10 })),
      http.post('/api/v1/organizer/events', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(eventDetail({ title: body.title }), { status: 201 })
      }),
      http.get(`/api/v1/organizer/events/${EVENT_ID}`, () => HttpResponse.json(eventDetail({ title: 'Sự kiện mới' }))),
      http.get(`/api/v1/venues/${VENUE.id}/seat-maps`, () => HttpResponse.json([SEAT_MAP])),
    )
    const user = userEvent.setup()
    const router = renderAt('/organizer/events')
    await user.click(await screen.findByRole('button', { name: 'Tạo sự kiện' }, T))

    const dialog = await screen.findByRole('dialog', { name: 'Tạo sự kiện mới' })
    await user.click(within(dialog).getByRole('button', { name: 'Tạo & tiếp tục' }))
    expect((await within(dialog).findAllByText('Không để trống')).length).toBeGreaterThanOrEqual(3)

    await user.type(within(dialog).getByLabelText(/Tên sự kiện/), 'Sự kiện mới')
    await user.click(within(dialog).getByRole('combobox', { name: /Danh mục/ }))
    await user.click(await screen.findByRole('option', { name: 'Nhạc sống' }))
    await user.click(within(dialog).getByRole('combobox', { name: /Địa điểm/ }))
    await user.click(await screen.findByRole('option', { name: 'Nhà hát Hòa Bình' }))
    await user.type(within(dialog).getByLabelText(/Ngày diễn/), '2026-11-15')
    await user.type(within(dialog).getByLabelText(/Giờ bắt đầu/), '20:00')
    await user.click(within(dialog).getByRole('button', { name: 'Tạo & tiếp tục' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`/organizer/events/${EVENT_ID}/edit`), T)
    expect(body).toEqual({ title: 'Sự kiện mới', category: 'Nhạc sống', venueId: VENUE.id, startsAt: '2026-11-15T13:00:00.000Z' })
  })
})
