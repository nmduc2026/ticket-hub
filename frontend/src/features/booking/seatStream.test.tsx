import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getGetEventSeatsQueryKey, type getEventSeatsResponse } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import { FakeEventSource, installFakeEventSource } from '@/test/fake-event-source'

import { applySeatChanges } from './seatChanges'
import { useSeatStream } from './useSeatStream'

const EVENT_ID = '00000000-0000-7000-8000-0000000d0001'

function snapshot(statuses: Record<string, string> = {}): getEventSeatsResponse {
  return {
    status: 200,
    headers: new Headers(),
    data: {
      eventId: EVENT_ID,
      eventStatus: 'ON_SALE',
      width: 100,
      height: 100,
      tiers: [],
      sections: [],
      elements: [],
      seats: ['s1', 's2', 's3'].map((id, index) => ({
        id,
        sectionCode: 'A',
        row: 'B',
        number: index + 1,
        x: 0,
        y: 0,
        status: statuses[id] ?? 'AVAILABLE',
      })),
    },
  } as getEventSeatsResponse
}

describe('áp thay đổi ghế lên snapshot (AC-6)', () => {
  it('chỉ ghế đổi có đối tượng mới, ghế khác giữ nguyên tham chiếu', () => {
    const before = snapshot()
    const after = applySeatChanges(before, [{ seatId: 's2', status: 'HELD' }])

    if (before.status !== 200 || after?.status !== 200) throw new Error('snapshot phải là 200')
    expect(after.data.seats.map((seat) => seat.status)).toEqual(['AVAILABLE', 'HELD', 'AVAILABLE'])
    expect(after.data.seats[0]).toBe(before.data.seats[0])
    expect(after.data.seats[2]).toBe(before.data.seats[2])
  })

  it('không đổi gì (ghế lạ, trạng thái trùng, chưa có snapshot) thì trả nguyên bản', () => {
    const before = snapshot({ s1: 'SOLD' })

    expect(applySeatChanges(before, [{ seatId: 'khong-co', status: 'HELD' }])).toBe(before)
    expect(applySeatChanges(before, [{ seatId: 's1', status: 'SOLD' }])).toBe(before)
    expect(applySeatChanges(undefined, [{ seatId: 's1', status: 'HELD' }])).toBeUndefined()
  })
})

describe('luồng SSE trạng thái ghế (AC-6)', () => {
  beforeEach(() => installFakeEventSource())
  afterEach(() => vi.unstubAllGlobals())

  function setup() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(getGetEventSeatsQueryKey(EVENT_ID), snapshot())
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    const hook = renderHook(() => useSeatStream(EVENT_ID), { wrapper })
    return { queryClient, invalidate, hook }
  }

  const statusOf = (queryClient: QueryClient, seatId: string) => {
    const cached = queryClient.getQueryData<getEventSeatsResponse>(getGetEventSeatsQueryKey(EVENT_ID))
    return cached?.status === 200 ? cached.data.seats.find((seat) => seat.id === seatId)?.status : undefined
  }

  it('nối tới /seats/stream; mở xong báo đang nghe và tải lại snapshot (tin lúc chưa nghe không được gửi lại)', async () => {
    const { hook, invalidate } = setup()
    expect(FakeEventSource.latest().url).toBe(`/api/v1/events/${EVENT_ID}/seats/stream`)
    expect(hook.result.current).toBe(false)

    act(() => FakeEventSource.latest().reconnect())

    await waitFor(() => expect(hook.result.current).toBe(true))
    expect(invalidate).toHaveBeenCalledWith({ queryKey: getGetEventSeatsQueryKey(EVENT_ID) })
  })

  it('tin seats áp thẳng lên cache theo seatId, không cần tải lại', async () => {
    const { queryClient } = setup()
    const source = FakeEventSource.latest()
    act(() => source.reconnect())

    act(() => source.emit('seats', { changes: [{ seatId: 's1', status: 'HELD' }, { seatId: 's3', status: 'SOLD' }] }))

    expect(statusOf(queryClient, 's1')).toBe('HELD')
    expect(statusOf(queryClient, 's2')).toBe('AVAILABLE')
    expect(statusOf(queryClient, 's3')).toBe('SOLD')
  })

  it('SSE đứt → báo không nghe (rơi về polling); nối lại → nghe và tải lại snapshot', async () => {
    const { hook, invalidate } = setup()
    const source = FakeEventSource.latest()
    act(() => source.reconnect())
    await waitFor(() => expect(hook.result.current).toBe(true))
    invalidate.mockClear()

    act(() => source.fail())
    await waitFor(() => expect(hook.result.current).toBe(false))

    act(() => source.reconnect())
    await waitFor(() => expect(hook.result.current).toBe(true))
    expect(invalidate).toHaveBeenCalledTimes(1)
  })

  it('rời trang thì đóng luồng', () => {
    const { hook } = setup()
    const source = FakeEventSource.latest()

    hook.unmount()

    expect(source.readyState).toBe(FakeEventSource.CLOSED)
  })
})
