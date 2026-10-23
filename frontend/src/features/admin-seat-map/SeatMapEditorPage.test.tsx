import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { vi } from 'vitest'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

// Canvas không chạy trong jsdom; kiểm cột Lớp / Thuộc tính / thanh công cụ. Hình vẽ kiểm bằng trình duyệt thật.
vi.mock('react-konva', () => {
  const Passthrough = ({ children }: { children?: React.ReactNode }) => <>{children}</>
  const Nothing = () => null
  return { Stage: Passthrough, Layer: Passthrough, Group: Passthrough, Rect: Nothing, Line: Nothing, Text: Nothing, Shape: Nothing }
})

function seatMap(overrides: Record<string, unknown> = {}) {
  return {
    id: 'm1',
    name: 'Khán phòng chính',
    version: 1,
    width: 1000,
    height: 640,
    locked: false,
    sections: [
      { id: 's1', code: 'A', name: 'Khu A', seatCount: 50, shape: { kind: 'GRID', x: 100, y: 200, rotation: 0, rows: 5, seatsPerRow: 10, rowGap: 28, seatGap: 28, curve: 0, numbering: 'LTR', hiddenSeats: [] } },
    ],
    elements: [{ id: 'e1', type: 'STAGE', label: 'Sân khấu chính', shape: { x: 300, y: 40, w: 400, h: 80, rotation: 0 } }],
    ...overrides,
  }
}

function renderEditor() {
  mockMe(meResponse(['CUSTOMER', 'ADMIN']))
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: ['/admin/venues/v1/seat-maps/m1'] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe('Editor sơ đồ ghế', () => {
  it('hiện khu và phần tử ở cột Lớp; sửa tên khu rồi lưu gửi đúng tên mới; hoàn tác trả lại', async () => {
    let saved: { sections: Array<{ name: string }> } | undefined
    server.use(
      http.get('/api/v1/admin/seat-maps/m1', () => HttpResponse.json(seatMap())),
      http.put('/api/v1/admin/seat-maps/m1', async ({ request }) => {
        saved = (await request.json()) as typeof saved
        return HttpResponse.json(seatMap())
      }),
    )
    const user = userEvent.setup()
    renderEditor()

    expect(await screen.findByRole('heading', { name: 'Khán phòng chính' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Khu A/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Sân khấu chính/ })).toBeInTheDocument()
    expect(screen.getByText('Tổng ghế bán: 50')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lưu sơ đồ' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /Khu A/ }))
    const name = screen.getByRole('textbox', { name: 'Tên khu' })
    await user.clear(name)
    await user.type(name, 'Khu VIP{Enter}')

    expect(screen.getByText('Chưa lưu')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Khu VIP/ })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Hoàn tác' }))
    expect(screen.getByRole('button', { name: /Khu A/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Làm lại' }))
    await user.click(screen.getByRole('button', { name: 'Lưu sơ đồ' }))

    await waitFor(() => expect(saved?.sections[0].name).toBe('Khu VIP'))
  })

  it('đổi số hàng làm đổi tổng ghế; thêm khu mới lấy mã K1; xoá khu cần bấm hai lần', async () => {
    server.use(http.get('/api/v1/admin/seat-maps/m1', () => HttpResponse.json(seatMap())))
    const user = userEvent.setup()
    renderEditor()

    await user.click(await screen.findByRole('button', { name: /Khu A/ }))
    const rows = screen.getByRole('textbox', { name: 'Số hàng' })
    await user.clear(rows)
    await user.type(rows, '8{Enter}')
    expect(screen.getByText('Tổng ghế bán: 80')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Thêm' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Khu ghế' }))
    expect(screen.getByRole('button', { name: /Khu K1/ })).toBeInTheDocument()
    expect(screen.getByText('Tổng ghế bán: 130')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Xoá khu' }))
    expect(screen.getByText('Tổng ghế bán: 130')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Bấm lần nữa để xoá' }))
    expect(screen.getByText('Tổng ghế bán: 80')).toBeInTheDocument()
  })

  it('sơ đồ đã khoá: chỉ xem, có thông báo và nút "Tạo bản sao để sửa", không có nút lưu', async () => {
    let copied = false
    server.use(
      http.get('/api/v1/admin/seat-maps/m1', () => HttpResponse.json(seatMap({ locked: true }))),
      http.post('/api/v1/admin/seat-maps/m1/copy', () => {
        copied = true
        return HttpResponse.json(seatMap({ id: 'm2', version: 2, locked: false }), { status: 201 })
      }),
      http.get('/api/v1/admin/seat-maps/m2', () => HttpResponse.json(seatMap({ id: 'm2', version: 2 }))),
    )
    const user = userEvent.setup()
    renderEditor()

    expect(await screen.findByRole('status')).toHaveTextContent('Sơ đồ đã có sự kiện publish dùng')
    expect(screen.queryByRole('button', { name: 'Lưu sơ đồ' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Khu A/ }))
    expect(screen.getByRole('textbox', { name: 'Tên khu' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Tạo bản sao để sửa' }))
    await waitFor(() => expect(copied).toBe(true))
    expect(await screen.findByText('Phiên bản 2')).toBeInTheDocument()
  })
})
