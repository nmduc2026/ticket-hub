import { useListVenueSeatMaps } from '@/api/generated/endpoints/venue-controller/venue-controller'
import type { OrganizerEventDetail, SeatMap } from '@/api/generated/model'

/** Sơ đồ ghế đã chọn cho sự kiện (khu, sân khấu, số ghế); `undefined` khi chưa chọn địa điểm / sơ đồ hoặc đang tải. */
export function useEventSeatMap(event: OrganizerEventDetail | undefined): SeatMap | undefined {
  const venueId = event?.venueId
  const query = useListVenueSeatMaps(venueId ?? '', { query: { enabled: Boolean(venueId), staleTime: 5 * 60_000 } })
  const maps = query.data?.status === 200 ? query.data.data : []
  return maps.find((map) => map.id === event?.seatMapId)
}
