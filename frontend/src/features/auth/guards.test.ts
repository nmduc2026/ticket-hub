import { QueryClient } from '@tanstack/react-query'
import { isRedirect } from '@tanstack/react-router'
import { vi } from 'vitest'

import { meResponse, mockMe } from '@/test/server'

import { loadCurrentUser, requireRole } from './guards'

const redirectToLogin = vi.hoisted(() => vi.fn())
vi.mock('@/lib/auth', () => ({ redirectToLogin }))

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

describe('requireRole', () => {
  beforeEach(() => redirectToLogin.mockClear())

  it('cho qua khi người dùng có đúng role', async () => {
    mockMe(meResponse(['CUSTOMER', 'ADMIN']))

    const user = await requireRole(newClient(), 'ADMIN', '/admin')

    expect(user.roles).toContain('ADMIN')
    expect(redirectToLogin).not.toHaveBeenCalled()
  })

  it('chuyển CUSTOMER vào /admin sang /forbidden', async () => {
    mockMe(meResponse(['CUSTOMER']))

    const failure = await requireRole(newClient(), 'ADMIN', '/admin').catch((error: unknown) => error)

    expect(isRedirect(failure)).toBe(true)
    expect(failure).toMatchObject({ options: { to: '/forbidden' } })
    expect(redirectToLogin).not.toHaveBeenCalled()
  })

  it('chưa đăng nhập thì chuyển tới đăng nhập và nhớ trang đang vào', async () => {
    mockMe(null)

    const pending = requireRole(newClient(), 'ADMIN', '/admin?tab=users')
    const settled = await Promise.race([
      pending.then(() => 'resolved'),
      new Promise((resolve) => setTimeout(() => resolve('waiting'), 50)),
    ])

    expect(redirectToLogin).toHaveBeenCalledWith('/admin?tab=users')
    expect(settled).toBe('waiting')
  })
})

describe('loadCurrentUser', () => {
  it('trả null khi 401, không ném lỗi', async () => {
    mockMe(null)

    await expect(loadCurrentUser(newClient())).resolves.toBeNull()
  })
})
