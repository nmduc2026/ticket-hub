import { useEffect, useRef, useState } from 'react'

import { getMe } from '@/api/generated/endpoints/me-controller/me-controller'
import { getQueueStatus, getStreamQueueUrl, joinQueue } from '@/api/generated/endpoints/queue-controller/queue-controller'
import type { QueueStatusResponse } from '@/api/generated/model'
import { ApiError } from '@/lib/api-client'
import { openSse, parseSse } from '@/lib/sse'

import {
  applyAdmitted,
  applyClosed,
  applyStatus,
  INITIAL_VIEW,
  type CloseReason,
  type QueueView,
  type UnavailableReason,
} from './queueState'

/** Giữ phiên đăng nhập: SSE không gia hạn session nên gọi `GET /me` định kỳ (overview.md §7.1). */
const KEEP_ALIVE_MS = 5 * 60_000
/** SSE bị từ chối hẳn thì hỏi `status` định kỳ. */
const POLL_MS = 5_000

const UNAVAILABLE_BY_CODE: Record<string, UnavailableReason> = {
  QUEUE_NOT_OPEN: 'NOT_OPEN',
  QUEUE_NOT_ENABLED: 'NOT_ENABLED',
  EVENT_NOT_FOUND: 'NOT_FOUND',
}

/**
 * Vào phòng chờ của sự kiện và theo dõi vị trí (màn #4). Vào hàng bằng `join` (vào lại không đổi vị trí), nghe SSE
 * `position` / `admitted` / `closed`; nối lại thì hỏi `status` rồi nghe tiếp; SSE bị từ chối thì polling `status`.
 * Đến lượt hoặc phòng chờ đóng thì ngừng nghe.
 */
export function useQueueRoom(eventId: string): QueueView {
  const [view, setView] = useState<QueueView>(INITIAL_VIEW)
  const stop = useRef<() => void>(() => undefined)

  useEffect(() => {
    let cancelled = false
    let closeSse: (() => void) | undefined
    let poller: ReturnType<typeof setInterval> | undefined
    let terminal = false

    stop.current = () => {
      terminal = true
      closeSse?.()
      if (poller) clearInterval(poller)
    }

    const update = (next: (current: QueueView) => QueueView) => {
      if (!cancelled) setView(next)
    }
    const applyResponse = (status: QueueStatusResponse) => update((current) => applyStatus(current, status, Date.now()))

    const fail = (error: unknown) => {
      if (error instanceof ApiError && error.code === 'QUEUE_CLOSED') {
        update(() => ({ kind: 'closed', reason: 'UNKNOWN' }))
        return
      }
      const reason = (error instanceof ApiError && error.code && UNAVAILABLE_BY_CODE[error.code]) || 'ERROR'
      update(() => ({ kind: 'unavailable', reason }))
    }

    const syncStatus = async () => {
      try {
        const response = await getQueueStatus(eventId)
        if (response.status === 200) applyResponse(response.data)
      } catch (error) {
        if (error instanceof ApiError && error.code === 'QUEUE_NOT_JOINED') {
          // Hàng chờ mất dữ liệu (Redis khởi động lại): xếp hàng lại.
          await start()
          return
        }
        fail(error)
      }
    }

    const startPolling = () => {
      poller ??= setInterval(() => {
        if (!terminal) void syncStatus()
      }, POLL_MS)
    }

    const listen = () => {
      closeSse = openSse(getStreamQueueUrl(eventId), {
        handlers: {
          position: (data) => {
            const status = parseSse<QueueStatusResponse>(data)
            if (status) applyResponse(status)
          },
          admitted: (data) => {
            const payload = parseSse<{ admissionExpiresAt: string }>(data)
            if (payload) update((current) => applyAdmitted(current, payload.admissionExpiresAt))
          },
          closed: (data) => {
            const payload = parseSse<{ reason: CloseReason }>(data)
            if (payload) update(() => applyClosed(payload.reason))
          },
        },
        onReconnect: () => void syncStatus(),
        onFailure: startPolling,
      })
    }

    const start = async () => {
      try {
        const response = await joinQueue(eventId)
        if (response.status !== 202) return
        applyResponse(response.data)
        // Đã có lượt vào thì không cần nghe thêm.
        if (!cancelled && !closeSse && response.data.phase !== 'ADMITTED') listen()
      } catch (error) {
        fail(error)
      }
    }

    void start()
    const keepAlive = setInterval(() => {
      // Best-effort: lỗi ở đây không báo người dùng; request thật kế tiếp sẽ gặp 401 và được chuyển tới đăng nhập.
      getMe().catch(() => undefined)
    }, KEEP_ALIVE_MS)

    return () => {
      cancelled = true
      stop.current()
      clearInterval(keepAlive)
    }
  }, [eventId])

  // Đến lượt hoặc phòng chờ đóng thì không cần giữ kết nối nữa.
  useEffect(() => {
    if (view.kind === 'admitted' || view.kind === 'closed' || view.kind === 'unavailable') stop.current()
  }, [view.kind])

  return view
}
