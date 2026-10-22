import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getGetMyUnreadNotificationCountQueryKey } from '@/api/generated/endpoints/my-notification-controller/my-notification-controller'
import { FakeEventSource, installFakeEventSource } from '@/test/fake-event-source'

import { useNotificationStream } from './useNotificationStream'

describe('luồng SSE thông báo (AC-7)', () => {
  beforeEach(() => installFakeEventSource())
  afterEach(() => vi.unstubAllGlobals())

  function setup(enabled = true) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    const hook = renderHook(() => useNotificationStream(enabled), { wrapper })
    return { queryClient, invalidate, hook }
  }

  it('unread cập nhật thẳng số chưa đọc của chuông, không cần tải lại trang', async () => {
    const { queryClient, hook } = setup()
    expect(FakeEventSource.latest().url).toBe('/api/v1/me/notifications/stream')
    act(() => FakeEventSource.latest().reconnect())
    await waitFor(() => expect(hook.result.current).toBe(true))

    act(() => FakeEventSource.latest().emit('unread', { count: 4 }))

    expect(queryClient.getQueryData(getGetMyUnreadNotificationCountQueryKey())).toMatchObject({ status: 200, data: { count: 4 } })
  })

  it('notification mới làm mới các danh sách thông báo đang mở', () => {
    const { invalidate } = setup()
    act(() => FakeEventSource.latest().reconnect())

    act(() => FakeEventSource.latest().emit('notification', { id: 'n1', title: 'Vé của bạn đã sẵn sàng' }))

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['/api/v1/me/notifications'] })
  })

  it('SSE lỗi → báo không nghe để chuông rơi về polling 60 giây', async () => {
    const { hook } = setup()
    act(() => FakeEventSource.latest().reconnect())
    await waitFor(() => expect(hook.result.current).toBe(true))

    act(() => FakeEventSource.latest().fail())

    await waitFor(() => expect(hook.result.current).toBe(false))
  })

  it('khách chưa đăng nhập không mở luồng', () => {
    setup(false)

    expect(FakeEventSource.instances).toHaveLength(0)
  })
})
