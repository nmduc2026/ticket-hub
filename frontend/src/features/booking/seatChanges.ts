import type { getEventSeatsResponse } from '@/api/generated/endpoints/public-event-controller/public-event-controller'

/** Một thay đổi trạng thái ghế từ SSE `seats` (overview.md §12). */
export interface SeatChange {
  seatId: string
  status: 'AVAILABLE' | 'HELD' | 'SOLD'
}

/**
 * Áp thay đổi lên snapshot sơ đồ ghế đang có trong cache, theo `seatId`: chỉ ghế đổi mới có đối tượng mới nên sơ đồ
 * không phải vẽ lại toàn bộ. Ghế không có trong snapshot (không bán) hoặc snapshot chưa tải thì bỏ qua.
 */
export function applySeatChanges(
  cached: getEventSeatsResponse | undefined,
  changes: readonly SeatChange[],
): getEventSeatsResponse | undefined {
  if (!cached || cached.status !== 200 || changes.length === 0) return cached
  const byId = new Map(changes.map((change) => [change.seatId, change.status]))
  let changed = false
  const seats = cached.data.seats.map((seat) => {
    const status = byId.get(seat.id)
    if (status === undefined || status === seat.status) return seat
    changed = true
    return { ...seat, status }
  })
  return changed ? { ...cached, data: { ...cached.data, seats } } : cached
}
