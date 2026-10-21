import '@testing-library/jest-dom/vitest'

import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'

import '@/i18n'
import { server } from './server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
})
afterAll(() => server.close())

// jsdom chưa cài scrollTo; router gọi khi chuyển trang.
window.scrollTo = () => {}

// Radix (Checkbox, Popover…) đo kích thước bằng ResizeObserver; jsdom chưa có.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// cmdk cuộn mục đang chọn vào khung nhìn; jsdom chưa cài scrollIntoView.
Element.prototype.scrollIntoView ??= () => {}
