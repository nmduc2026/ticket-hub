import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const APPLICATION = '/api/v1/organizer/application'

function problem(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } })
}

function application(overrides: Record<string, unknown>) {
  return {
    status: 'PENDING',
    submittedAt: '2026-10-21T03:00:00Z',
    type: 'BUSINESS',
    displayName: 'Saigon Sound',
    legalName: 'Công ty TNHH Saigon Sound',
    legalId: '0312345678',
    contactEmail: 'khoa@saigonsound.vn',
    contactPhone: '0908765432',
    introduction: 'Đêm nhạc indie',
    ...overrides,
  }
}

function renderApply() {
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: ['/organizer/apply'] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText(/Tên hiển thị/, undefined, { timeout: 5000 }), 'Saigon Sound')
  await user.type(screen.getByLabelText(/Tên doanh nghiệp/), 'Công ty TNHH Saigon Sound')
  await user.type(screen.getByLabelText(/Mã số thuế/), '0312345678')
  await user.type(screen.getByLabelText(/Email liên hệ/), 'khoa@saigonsound.vn')
  await user.type(screen.getByLabelText(/Số điện thoại/), '0908765432')
  await user.type(screen.getByLabelText(/Giới thiệu/), 'Đêm nhạc indie')
}

describe('đăng ký làm ban tổ chức (#22)', () => {
  it('chưa nộp hồ sơ: hiện form, bắt buộc điền và gửi đúng nội dung', async () => {
    mockMe(meResponse(['CUSTOMER']))
    let sent: Record<string, unknown> | undefined
    server.use(
      http.get(APPLICATION, () => problem(404, 'APPLICATION_NOT_FOUND')),
      http.post('/api/v1/organizer/apply', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ status: 'PENDING' }, { status: 201 })
      }),
    )
    const user = userEvent.setup({ delay: null })
    renderApply()

    await user.click(await screen.findByRole('button', { name: 'Gửi hồ sơ' }, { timeout: 5000 }))
    expect(screen.getAllByText('Không để trống').length).toBeGreaterThan(3)
    expect(sent).toBeUndefined()

    server.use(http.get(APPLICATION, () => HttpResponse.json(application({}))))
    await fillForm(user)
    await user.click(screen.getByRole('button', { name: 'Gửi hồ sơ' }))

    await waitFor(() => expect(sent).toMatchObject({ type: 'BUSINESS', displayName: 'Saigon Sound', legalId: '0312345678' }))
    expect(await screen.findByText('Hồ sơ đang chờ duyệt')).toBeInTheDocument()
  }, 20_000)

  it('lỗi mã số do server kiểm hiện ngay dưới ô mã số', async () => {
    mockMe(meResponse(['CUSTOMER']))
    server.use(
      http.get(APPLICATION, () => problem(404, 'APPLICATION_NOT_FOUND')),
      http.post('/api/v1/organizer/apply', () => problem(422, 'LEGAL_ID_INVALID')),
    )
    const user = userEvent.setup({ delay: null })
    renderApply()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: 'Gửi hồ sơ' }))

    expect(await screen.findByText(/Mã số thuế gồm 10 hoặc 13 chữ số/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Mã số thuế/)).toHaveAttribute('aria-invalid', 'true')
  }, 20_000)

  it('chờ duyệt: hiện trạng thái, không còn form', async () => {
    mockMe(meResponse(['CUSTOMER']))
    server.use(http.get(APPLICATION, () => HttpResponse.json(application({}))))
    renderApply()

    expect(await screen.findByText('Hồ sơ đang chờ duyệt', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText(/Đã nhận hồ sơ của Saigon Sound/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Gửi hồ sơ' })).not.toBeInTheDocument()
  })

  it('bị từ chối: hiện lý do và form điền sẵn để gửi lại', async () => {
    mockMe(meResponse(['CUSTOMER']))
    server.use(
      http.get(APPLICATION, () => HttpResponse.json(application({ status: 'REJECTED', rejectionReason: 'Ảnh giấy phép bị mờ' }))),
    )
    renderApply()

    expect(await screen.findByText('Hồ sơ chưa được duyệt', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('Ảnh giấy phép bị mờ')).toBeInTheDocument()
    expect(screen.getByLabelText(/Tên hiển thị/)).toHaveValue('Saigon Sound')
    expect(screen.getByRole('button', { name: 'Gửi lại hồ sơ' })).toBeInTheDocument()
  })

  it('đã duyệt: nhắc đăng nhập lại; đã có role thì có nút mở trang Ban tổ chức', async () => {
    mockMe(meResponse(['CUSTOMER']))
    server.use(http.get(APPLICATION, () => HttpResponse.json(application({ status: 'APPROVED' }))))
    renderApply()

    expect(await screen.findByText('Hồ sơ đã được duyệt', undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('Đăng nhập lại để thấy menu Ban tổ chức.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Mở trang Ban tổ chức' })).not.toBeInTheDocument()
  })

  it('người đã có role ORGANIZER thấy nút mở trang Ban tổ chức', async () => {
    mockMe(meResponse(['CUSTOMER', 'ORGANIZER']))
    server.use(http.get(APPLICATION, () => HttpResponse.json(application({ status: 'APPROVED' }))))
    renderApply()

    expect(await screen.findByRole('link', { name: 'Mở trang Ban tổ chức' }, { timeout: 5000 })).toBeInTheDocument()
  })
})
