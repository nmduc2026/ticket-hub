import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

function organizer(index: number, overrides: Record<string, unknown> = {}) {
  return {
    id: `o${index}`,
    displayName: `Ban tổ chức ${index}`,
    email: `bto${index}@example.test`,
    status: 'PENDING',
    submittedAt: '2026-10-21T03:00:00Z',
    type: 'BUSINESS',
    legalName: `Công ty ${index}`,
    legalId: '0312345678',
    introduction: 'Đêm nhạc indie',
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

describe('hồ sơ organizer của Admin (#15)', () => {
  beforeEach(() => mockMe(meResponse(['CUSTOMER', 'ADMIN'])))

  it('lọc trạng thái ghi lên URL và gửi lên API', async () => {
    const requests: URL[] = []
    server.use(
      http.get('/api/v1/admin/organizers', ({ request }) => {
        requests.push(new URL(request.url))
        return page([organizer(1)])
      }),
    )
    const user = userEvent.setup()
    const router = renderAt('/admin/organizers')
    await screen.findByText('Ban tổ chức 1', undefined, { timeout: 5000 })

    await user.click(screen.getByRole('button', { name: /Trạng thái/ }))
    await user.click(await screen.findByRole('radio', { name: 'Chờ duyệt' }))

    await waitFor(() => expect(router.state.location.search).toMatchObject({ status: 'PENDING' }))
    await waitFor(() => expect(requests.at(-1)?.searchParams.get('status')).toBe('PENDING'))
  })

  it('duyệt hàng loạt 3 hồ sơ gửi đủ 3 id và báo hồ sơ bị bỏ qua', async () => {
    let bulkBody: { ids: string[] } | undefined
    server.use(
      http.get('/api/v1/admin/organizers', () => page([organizer(1), organizer(2), organizer(3)])),
      http.post('/api/v1/admin/organizers/bulk-approve', async ({ request }) => {
        bulkBody = (await request.json()) as { ids: string[] }
        return HttpResponse.json({
          approved: ['o1', 'o2'],
          skipped: [{ id: 'o3', code: 'ORGANIZER_NOT_PENDING' }],
        })
      }),
    )
    const user = userEvent.setup()
    renderAt('/admin/organizers')
    await screen.findByText('Ban tổ chức 3', undefined, { timeout: 5000 })

    await user.click(screen.getByRole('checkbox', { name: 'Chọn tất cả dòng' }))
    await user.click(screen.getByRole('button', { name: 'Duyệt (3)' }))

    await waitFor(() => expect(bulkBody?.ids).toEqual(['o1', 'o2', 'o3']))
    expect(await screen.findByText('Đã duyệt 2 hồ sơ')).toBeInTheDocument()
    expect(screen.getByText('1 hồ sơ bỏ qua vì không còn chờ duyệt')).toBeInTheDocument()
  })

  it('từ chối bắt buộc có lý do rồi mới gửi', async () => {
    let rejectBody: { reason: string } | undefined
    server.use(
      http.get('/api/v1/admin/organizers', () => page([organizer(1)])),
      http.post('/api/v1/admin/organizers/o1/reject', async ({ request }) => {
        rejectBody = (await request.json()) as { reason: string }
        return HttpResponse.json({ status: 'REJECTED' })
      }),
    )
    const user = userEvent.setup()
    renderAt('/admin/organizers')
    await screen.findByText('Ban tổ chức 1', undefined, { timeout: 5000 })

    await user.click(screen.getByRole('button', { name: /Ban tổ chức 1/, description: undefined, hidden: false, expanded: false }))
    await user.click(await screen.findByRole('menuitem', { name: 'Từ chối' }))
    await user.click(await screen.findByRole('button', { name: 'Từ chối hồ sơ' }))
    expect(await screen.findByText('Nhập lý do từ chối')).toBeInTheDocument()
    expect(rejectBody).toBeUndefined()

    await user.type(screen.getByLabelText(/Lý do/), 'Ảnh giấy phép bị mờ')
    await user.click(screen.getByRole('button', { name: 'Từ chối hồ sơ' }))

    await waitFor(() => expect(rejectBody).toEqual({ reason: 'Ảnh giấy phép bị mờ' }))
    expect(await screen.findByText('Đã từ chối hồ sơ')).toBeInTheDocument()
  })

  it('xem hồ sơ mở side sheet theo URL và hồ sơ đã xử lý không có nút duyệt', async () => {
    server.use(
      http.get('/api/v1/admin/organizers', () =>
        page([organizer(1, { status: 'REJECTED', rejectionReason: 'Thiếu giấy phép' })]),
      ),
    )
    const router = renderAt('/admin/organizers?view=o1')

    expect(await screen.findByRole('dialog', undefined, { timeout: 5000 })).toHaveTextContent('Thiếu giấy phép')
    expect(router.state.location.search).toMatchObject({ view: 'o1' })
  })
})
