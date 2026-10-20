import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

import type { MeResponse } from '@/api/generated/model'

export const server = setupServer()

export function meResponse(roles: string[], overrides: Partial<MeResponse> = {}): MeResponse {
  return {
    id: '00000000-0000-7000-8000-000000000001',
    email: 'customer@tickethub.local',
    fullName: 'Khách Hàng',
    username: 'customer',
    roles,
    enabled: true,
    ...overrides,
  }
}

/** Giả lập `GET /api/v1/me` theo OpenAPI: 200 với hồ sơ, hoặc 401 Problem Details khi chưa đăng nhập. */
export function mockMe(user: MeResponse | null) {
  server.use(
    http.get('/api/v1/me', () =>
      user
        ? HttpResponse.json(user)
        : HttpResponse.json(
            { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHENTICATED' },
            { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
          ),
    ),
  )
}
