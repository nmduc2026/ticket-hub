import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import {
  getGetMyUnreadNotificationCountQueryKey,
  getStreamMyNotificationsUrl,
  type getMyUnreadNotificationCountResponse,
} from '@/api/generated/endpoints/my-notification-controller/my-notification-controller'
import { openSse, parseSse } from '@/lib/sse'

/** Khoá chung của mọi danh sách thông báo (các tham số lọc nối phía sau). */
const LIST_KEY = ['/api/v1/me/notifications']

/**
 * Thông báo realtime cho chuông và trang Thông báo (overview.md §12): `unread` cập nhật số chưa đọc, `notification`
 * làm mới các danh sách đang mở. Trả `true` khi luồng đang mở; khi đứt, nơi gọi rơi về polling 60 giây.
 */
export function useNotificationStream(enabled: boolean): boolean {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!enabled) return undefined
    const close = openSse(getStreamMyNotificationsUrl(), {
      handlers: {
        unread: (data) => {
          const payload = parseSse<{ count: number }>(data)
          if (!payload) return
          const next: getMyUnreadNotificationCountResponse = { status: 200, data: { count: payload.count }, headers: new Headers() }
          queryClient.setQueryData(getGetMyUnreadNotificationCountQueryKey(), next)
        },
        notification: () => void queryClient.invalidateQueries({ queryKey: LIST_KEY }),
      },
      onOpen: () => setConnected(true),
      onDown: () => setConnected(false),
    })
    return () => {
      close()
      setConnected(false)
    }
  }, [enabled, queryClient])

  return connected
}
