import { useGetOrganizerEvent } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import type { OrganizerEventDetail } from '@/api/generated/model'
import { ApiError } from '@/lib/api-client'

export interface OrganizerEventResult {
  event: OrganizerEventDetail | undefined
  isLoading: boolean
  isError: boolean
  /** Sự kiện không có hoặc không phải của organizer này (404, không lộ sự kiện của người khác: AC-4). */
  notFound: boolean
  refetch: () => void
}

/** Chi tiết một sự kiện của tôi, đã bóc khỏi lớp bọc `{ data, status }` của Orval; các tab con cùng dùng một cache. */
export function useOrganizerEvent(eventId: string): OrganizerEventResult {
  const query = useGetOrganizerEvent(eventId, { query: { staleTime: 10_000, retry: false } })
  const error: unknown = query.error
  return {
    event: query.data?.status === 200 ? query.data.data : undefined,
    isLoading: query.isPending,
    isError: query.isError,
    notFound: error instanceof ApiError && error.status === 404,
    refetch: () => void query.refetch(),
  }
}
