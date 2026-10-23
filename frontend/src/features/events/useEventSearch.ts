import type { PublicEventPage, SearchEventsParams } from '@/api/generated/model'
import { useSearchEvents } from '@/api/generated/endpoints/public-event-controller/public-event-controller'

export interface EventSearchResult {
  page: PublicEventPage | null
  isLoading: boolean
  isError: boolean
  refetch: () => void
}

/** Tìm sự kiện công khai; trả về trang kết quả đã bóc khỏi lớp bọc `{ data, status }` của Orval. */
export function useEventSearch(params: SearchEventsParams): EventSearchResult {
  const query = useSearchEvents(params, { query: { staleTime: 30_000 } })
  return {
    page: query.data?.status === 200 ? query.data.data : null,
    isLoading: query.isPending,
    isError: query.isError,
    refetch: () => void query.refetch(),
  }
}
