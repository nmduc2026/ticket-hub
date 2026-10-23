import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { getGetEventSeatsQueryKey } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import { getStreamSeatsUrl } from '@/api/generated/endpoints/seat-stream-controller/seat-stream-controller'
import { openSse, parseSse } from '@/lib/sse'

import { applySeatChanges, type SeatChange } from './seatChanges'

/**
 * Trạng thái ghế realtime cho sơ đồ (màn #5, overview.md §12). Mỗi tin `seats` được áp thẳng lên snapshot trong cache
 * theo `seatId`. Mỗi lần kết nối mở (kể cả nối lại sau khi đứt) tải lại snapshot, vì tin phát lúc đứt không được gửi
 * lại và snapshot có thể đã tải trước khi nghe. Trả `true` khi luồng đang mở: nơi gọi tắt polling, rơi về polling khi
 * luồng đứt.
 */
export function useSeatStream(eventId: string): boolean {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const key = getGetEventSeatsQueryKey(eventId)
    const close = openSse(getStreamSeatsUrl(eventId), {
      handlers: {
        seats: (data) => {
          const payload = parseSse<{ changes: SeatChange[] }>(data)
          if (payload) queryClient.setQueryData(key, (cached: Parameters<typeof applySeatChanges>[0]) => applySeatChanges(cached, payload.changes))
        },
      },
      onOpen: () => {
        setConnected(true)
        void queryClient.invalidateQueries({ queryKey: key })
      },
      onDown: () => setConnected(false),
    })
    return () => {
      close()
      setConnected(false)
    }
  }, [eventId, queryClient])

  return connected
}
