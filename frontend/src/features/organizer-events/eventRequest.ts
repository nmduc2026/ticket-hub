import { getGetOrganizerEventQueryKey } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import type { EventRequest, OrganizerEventDetail } from '@/api/generated/model'
import type { QueryClient } from '@tanstack/react-query'

/**
 * Backend nhận đủ mọi trường mỗi lần sửa; sau publish các trường bán vé phải giữ nguyên, nếu không `EVENT_LOCKED`
 * (BR-14). Nên mọi lần lưu bắt đầu từ chi tiết hiện tại rồi chỉ ghi đè phần form đã đổi: chỗ duy nhất dựng request.
 */
export function toEventRequest(detail: OrganizerEventDetail, changes: Partial<EventRequest> = {}): EventRequest {
  return {
    title: detail.title,
    description: detail.description,
    category: detail.category,
    venueId: detail.venueId,
    seatMapId: detail.seatMapId,
    startsAt: detail.startsAt,
    endsAt: detail.endsAt,
    saleStartsAt: detail.saleStartsAt,
    maxTicketsPerUser: detail.maxTicketsPerUser,
    ...changes,
  }
}

const LIST_KEY = ['/api/v1/organizer/events']

/** Sau khi ghi: tải lại chi tiết sự kiện và mọi danh sách sự kiện của organizer. */
export function invalidateOrganizerEvent(queryClient: QueryClient, eventId: string): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: getGetOrganizerEventQueryKey(eventId) }),
    queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  ])
}
