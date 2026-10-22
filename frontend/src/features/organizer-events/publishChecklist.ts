import type { OrganizerEventDetail } from '@/api/generated/model'

/** Mã thiếu khớp `missing` của lỗi PUBLISH_INCOMPLETE ở backend (EventLifecycleService.requireComplete). */
export type PublishItem =
  | 'VENUE'
  | 'SEAT_MAP'
  | 'STARTS_AT'
  | 'ENDS_AT'
  | 'SALE_STARTS_AT'
  | 'TICKET_TIERS'
  | 'TIER_SECTIONS'

export type EditTab = 'dashboard' | 'info' | 'sales' | 'tiers'

export interface ChecklistEntry {
  item: PublishItem
  done: boolean
  /** Tab chứa chỗ sửa mục này. */
  tab: EditTab
}

/**
 * Điều kiện publish tính từ chi tiết sự kiện, để chỉ ra mục còn thiếu trước khi gọi server (BR-05 do server kiểm
 * thêm: organizer đã duyệt và nhận được tiền). Server vẫn là nơi quyết định cuối cùng.
 */
export function publishChecklist(event: OrganizerEventDetail): ChecklistEntry[] {
  return [
    { item: 'VENUE', done: Boolean(event.venueId), tab: 'info' },
    { item: 'SEAT_MAP', done: Boolean(event.seatMapId), tab: 'info' },
    { item: 'STARTS_AT', done: Boolean(event.startsAt), tab: 'info' },
    { item: 'ENDS_AT', done: Boolean(event.endsAt), tab: 'info' },
    { item: 'SALE_STARTS_AT', done: Boolean(event.saleStartsAt), tab: 'sales' },
    { item: 'TICKET_TIERS', done: event.tiers.length > 0, tab: 'tiers' },
    { item: 'TIER_SECTIONS', done: event.tierSections.length > 0, tab: 'tiers' },
  ]
}
