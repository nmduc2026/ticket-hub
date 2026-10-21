import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import type { Profile, UserSession } from '@/api/generated/model'
import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const T = { timeout: 5000 }
const HCM = { id: '00000000-0000-7000-8000-0000000a0001', code: '79', name: 'Thành phố Hồ Chí Minh', type: 'THANH_PHO', level: 1 }

const profile = (overrides: Partial<Profile> = {}): Profile => ({
  userId: '00000000-0000-7000-8000-000000000001',
  email: 'customer@tickethub.local',
  fullName: 'Khách Hàng',
  ...overrides,
})

const session = (id: string, current: boolean): UserSession => ({
  id,
  ipAddress: current ? '10.0.0.1' : '10.0.0.2',
  startedAt: '2026-10-20T01:00:00Z',
  lastAccessAt: '2026-10-21T01:00:00Z',
  current,
})

function problem(status: number, code: string) {
  return HttpResponse.json(
    { type: 'about:blank', title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function mockProfileApi(initial: Profile, sessions: UserSession[] = []) {
  const state = { profile: initial, sessions: [...sessions], updates: [] as unknown[], ended: [] as string[] }
  mockMe(meResponse(['CUSTOMER']))
  server.use(
    http.get('/api/v1/me/profile', () => HttpResponse.json(state.profile)),
    http.put('/api/v1/me/profile', async ({ request }) => {
      const body = (await request.json()) as { phone?: string; dateOfBirth?: string; provinceId?: string }
      state.updates.push(body)
      state.profile = { ...state.profile, phone: body.phone, dateOfBirth: body.dateOfBirth, province: body.provinceId ? HCM : undefined }
      return HttpResponse.json(state.profile)
    }),
    http.get('/api/v1/administrative-units', () => HttpResponse.json([HCM])),
    http.get('/api/v1/me/sessions', () => HttpResponse.json(state.sessions)),
    http.delete('/api/v1/me/sessions/:id', ({ params }) => {
      state.ended.push(String(params.id))
      state.sessions = state.sessions.filter((item) => item.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
    http.get('/api/v1/me/notification-preferences', () => HttpResponse.json([])),
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

describe('hồ sơ cá nhân (#19, AC-5)', () => {
  it('sửa SĐT, ngày sinh, tỉnh rồi lưu; email chỉ đọc kèm link đổi ở Keycloak', async () => {
    const state = mockProfileApi(profile())
    const user = userEvent.setup()
    renderAt('/me/profile')

    const phone = await screen.findByLabelText('Số điện thoại', undefined, T)
    expect(screen.getByText('customer@tickethub.local')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Đổi email/ })).toHaveAttribute('target', '_blank')

    await user.type(phone, '0912345678')
    await user.type(screen.getByLabelText('Ngày sinh'), '1995-04-30')
    await user.click(screen.getByRole('combobox', { name: 'Thành phố' }))
    await user.click(await screen.findByRole('option', { name: 'Thành phố Hồ Chí Minh' }))
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    await waitFor(() =>
      expect(state.updates).toEqual([{ phone: '0912345678', dateOfBirth: '1995-04-30', provinceId: HCM.id }]),
    )
    expect(await screen.findByText('Đã lưu hồ sơ')).toBeInTheDocument()
  })

  it('server trả PHONE_INVALID thì hiện lỗi trong trang', async () => {
    mockProfileApi(profile())
    server.use(http.put('/api/v1/me/profile', () => problem(422, 'PHONE_INVALID')))
    const user = userEvent.setup()
    renderAt('/me/profile')

    await user.type(await screen.findByLabelText('Số điện thoại', undefined, T), '123')
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    expect(await screen.findByText('Số điện thoại không hợp lệ.')).toBeInTheDocument()
  })

  it('ảnh sai loại bị chặn ở client, không gửi lên server', async () => {
    mockProfileApi(profile())
    let uploads = 0
    server.use(http.post('/api/v1/me/avatar', () => (uploads++, HttpResponse.json({ avatarUrl: 'x' }))))
    const user = userEvent.setup({ applyAccept: false })
    renderAt('/me/profile')

    await user.upload(await screen.findByLabelText('Tải ảnh lên', undefined, T), new File(['gif'], 'a.gif', { type: 'image/gif' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/jpg, png hoặc webp/i)
    expect(uploads).toBe(0)
  })
})

describe('phiên đăng nhập và xoá tài khoản (#19, AC-5)', () => {
  it('phiên hiện tại không có nút đăng xuất; đăng xuất phiên khác gọi DELETE đúng id', async () => {
    const state = mockProfileApi(profile(), [session('s-this', true), session('s-other', false)])
    const user = userEvent.setup()
    renderAt('/me/profile')

    await user.click(await screen.findByRole('tab', { name: 'Bảo mật' }, T))
    const list = await screen.findByRole('list', { name: 'Phiên đăng nhập' })
    const current = within(list).getByText('10.0.0.1').closest('li')
    const other = within(list).getByText('10.0.0.2').closest('li')
    expect(current && within(current).getByText('Thiết bị này')).toBeInTheDocument()
    expect(current && within(current).queryByRole('button', { name: 'Đăng xuất' })).toBeNull()

    if (!other) throw new Error('thiếu dòng phiên khác')
    await user.click(within(other).getByRole('button', { name: 'Đăng xuất' }))

    await waitFor(() => expect(state.ended).toEqual(['s-other']))
    await waitFor(() => expect(screen.queryByText('10.0.0.2')).not.toBeInTheDocument())
  })

  it('xoá tài khoản: nút xác nhận chỉ bật khi gõ đúng email; còn vé sắp diễn thì báo lý do', async () => {
    mockProfileApi(profile())
    let sent: unknown
    server.use(
      http.delete('/api/v1/me', async ({ request }) => {
        sent = await request.json()
        return problem(409, 'ACCOUNT_HAS_UPCOMING_TICKETS')
      }),
    )
    const user = userEvent.setup()
    renderAt('/me/profile')

    await user.click(await screen.findByRole('tab', { name: 'Bảo mật' }, T))
    await user.click(await screen.findByRole('button', { name: 'Xoá tài khoản' }))
    const dialog = await screen.findByRole('dialog')
    const confirm = within(dialog).getByRole('button', { name: 'Xoá tài khoản' })
    expect(confirm).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/Nhập customer@tickethub.local/), 'khac@tickethub.local')
    expect(confirm).toBeDisabled()
    await user.clear(within(dialog).getByLabelText(/Nhập customer@tickethub.local/))
    await user.type(within(dialog).getByLabelText(/Nhập customer@tickethub.local/), 'CUSTOMER@tickethub.local')
    await user.click(confirm)

    expect(await within(dialog).findByText(/còn vé sắp diễn ra/)).toBeInTheDocument()
    expect(sent).toEqual({ confirmEmail: 'CUSTOMER@tickethub.local' })
  })
})
