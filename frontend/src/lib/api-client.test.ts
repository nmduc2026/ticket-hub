import { http, HttpResponse } from 'msw'

import { server } from '@/test/server'

import { apiFetch, ApiError } from './api-client'

describe('apiFetch', () => {
  it('gửi cookie, header XHR và không có CSRF cho request đọc', async () => {
    let received: Request | undefined
    server.use(
      http.get('/api/v1/ping', ({ request }) => {
        received = request
        return HttpResponse.json({ ok: true })
      }),
    )

    const result = await apiFetch<{ data: { ok: boolean }; status: number }>('/api/v1/ping')

    expect(result.status).toBe(200)
    expect(result.data).toEqual({ ok: true })
    expect(received?.headers.get('x-requested-with')).toBe('XMLHttpRequest')
    expect(received?.headers.get('x-xsrf-token')).toBeNull()
    expect(received?.credentials).toBe('include')
  })

  it('gắn X-XSRF-TOKEN lấy từ cookie cho request ghi', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    let csrf: string | null = null
    server.use(
      http.post('/api/v1/things', ({ request }) => {
        csrf = request.headers.get('x-xsrf-token')
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const result = await apiFetch<{ status: number }>('/api/v1/things', { method: 'POST' })

    expect(csrf).toBe('token-123')
    expect(result.status).toBe(204)
  })

  it('ném ApiError mang code nghiệp vụ và traceId từ Problem Details', async () => {
    server.use(
      http.post('/api/v1/bookings', () =>
        HttpResponse.json(
          { status: 409, detail: 'Ghế vừa có người giữ', code: 'SEAT_UNAVAILABLE', traceId: 'abc123' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    const failure = await apiFetch('/api/v1/bookings', { method: 'POST' }).catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(ApiError)
    expect(failure).toMatchObject({ status: 409, code: 'SEAT_UNAVAILABLE', traceId: 'abc123' })
  })

  it('vẫn ném ApiError khi body lỗi không phải JSON', async () => {
    server.use(http.get('/api/v1/broken', () => new HttpResponse('<html>bad gateway</html>', { status: 502 })))

    const failure = await apiFetch('/api/v1/broken').catch((error: unknown) => error)

    expect(failure).toMatchObject({ status: 502, code: undefined })
  })
})
