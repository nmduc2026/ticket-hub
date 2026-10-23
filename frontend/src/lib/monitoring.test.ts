import * as Sentry from '@sentry/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@sentry/react', () => ({
  init: vi.fn(),
  captureException: vi.fn(),
  captureMessage: vi.fn(),
  addBreadcrumb: vi.fn(),
}))
vi.mock('web-vitals', () => ({ onLCP: vi.fn(), onINP: vi.fn(), onCLS: vi.fn() }))

async function load(dsn: string) {
  vi.resetModules()
  vi.stubEnv('VITE_SENTRY_DSN', dsn)
  return import('./monitoring')
}

afterEach(() => {
  vi.unstubAllEnvs()
  vi.clearAllMocks()
})

describe('monitoring', () => {
  it('không gửi gì khi chưa có DSN', async () => {
    const m = await load('')
    m.initMonitoring()
    m.captureError(new Error('x'))
    expect(Sentry.init).not.toHaveBeenCalled()
    expect(Sentry.captureException).not.toHaveBeenCalled()
  })

  it('có DSN thì khởi tạo Sentry và báo lỗi JS', async () => {
    const m = await load('https://k@example.ingest.sentry.io/1')
    m.initMonitoring()
    const error = new Error('lỗi giả lập')
    m.captureError(error)
    expect(Sentry.init).toHaveBeenCalledOnce()
    expect(Sentry.captureException).toHaveBeenCalledWith(error)
  })

  it('chỉ báo sự kiện khi web vital ở mức poor', async () => {
    const m = await load('https://k@example.ingest.sentry.io/1')
    m.reportVital({ name: 'LCP', value: 1200, rating: 'good', id: 'a' } as never)
    expect(Sentry.captureMessage).not.toHaveBeenCalled()
    m.reportVital({ name: 'LCP', value: 6000, rating: 'poor', id: 'b' } as never)
    expect(Sentry.captureMessage).toHaveBeenCalledOnce()
    expect(Sentry.addBreadcrumb).toHaveBeenCalledTimes(2)
  })
})
