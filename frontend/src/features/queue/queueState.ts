import type { QueueStatusResponse } from '@/api/generated/model'

export type CloseReason = 'SUSPENDED' | 'CANCELLED' | 'ENDED'

/** Lý do không vào được phòng chờ, để hiện đúng thông báo. */
export type UnavailableReason = 'NOT_OPEN' | 'NOT_ENABLED' | 'NOT_FOUND' | 'ERROR'

/** Khách đang ở đâu trong phòng chờ (overview.md §7.1). */
export type QueueView =
  | { kind: 'joining' }
  /** Vào trước giờ mở bán: thứ tự bốc thăm khi mở bán, chưa có vị trí. */
  | { kind: 'lottery' }
  | { kind: 'queued'; position: number; etaSeconds: number; initialPosition: number; updatedAt: number }
  | { kind: 'admitted'; expiresAt: string }
  | { kind: 'closed'; reason: CloseReason | 'UNKNOWN' }
  | { kind: 'unavailable'; reason: UnavailableReason }

export const INITIAL_VIEW: QueueView = { kind: 'joining' }

/** Áp trạng thái từ `join` / `status` / sự kiện `position` của SSE. `now` truyền vào để test không phải chờ. */
export function applyStatus(view: QueueView, status: QueueStatusResponse, now: number): QueueView {
  if (view.kind === 'closed' || view.kind === 'unavailable') return view
  if (status.phase === 'ADMITTED' && status.admissionExpiresAt) {
    return { kind: 'admitted', expiresAt: status.admissionExpiresAt }
  }
  if (status.phase === 'LOTTERY') return { kind: 'lottery' }
  if (status.position == null) return view
  const initialPosition = view.kind === 'queued' ? Math.max(view.initialPosition, status.position) : status.position
  return { kind: 'queued', position: status.position, etaSeconds: status.estimatedWaitSeconds ?? 0, initialPosition, updatedAt: now }
}

/** Sự kiện `admitted` của SSE: được gọi tên. */
export function applyAdmitted(view: QueueView, admissionExpiresAt: string): QueueView {
  if (view.kind === 'closed' || view.kind === 'unavailable') return view
  return { kind: 'admitted', expiresAt: admissionExpiresAt }
}

/** Sự kiện `closed` của SSE: sự kiện tạm dừng / huỷ / kết thúc. */
export function applyClosed(reason: CloseReason): QueueView {
  return { kind: 'closed', reason }
}

/** Tiến độ 0–100 cho thanh tiến độ: từ vị trí đầu tiên thấy được tới lượt. */
export function progressPercent(view: Extract<QueueView, { kind: 'queued' }>): number {
  if (view.initialPosition <= 0) return 100
  return Math.round((1 - view.position / view.initialPosition) * 100)
}
