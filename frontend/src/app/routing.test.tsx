import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen } from '@testing-library/react'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe } from '@/test/server'

function renderAt(path: string) {
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [path] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe('bảo vệ route theo role', () => {
  it('ADMIN vào được /admin', async () => {
    mockMe(meResponse(['CUSTOMER', 'ADMIN'], { fullName: 'Quản Trị', username: 'admin' }))

    renderAt('/admin')

    expect(await screen.findByRole('heading', { name: 'Tổng quan quản trị' })).toBeInTheDocument()
  })

  it('CUSTOMER vào /admin bị chặn và thấy trang không có quyền', async () => {
    mockMe(meResponse(['CUSTOMER']))

    renderAt('/admin')

    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Tổng quan quản trị' })).not.toBeInTheDocument()
  })

  it('CUSTOMER không thấy liên kết Quản trị trên thanh điều hướng', async () => {
    mockMe(meResponse(['CUSTOMER'], { fullName: 'Khách Hàng' }))

    renderAt('/')

    expect(await screen.findByText('Xin chào, Khách Hàng')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Quản trị' })).not.toBeInTheDocument()
  })

  it('khách chưa đăng nhập xem được trang chủ và thấy nút Đăng nhập', async () => {
    mockMe(null)

    renderAt('/')

    expect(await screen.findByRole('button', { name: 'Đăng nhập' })).toBeInTheDocument()
    expect(screen.getByText('Bạn chưa đăng nhập')).toBeInTheDocument()
  })
})
