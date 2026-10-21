import { useQuery } from '@tanstack/react-query'

import { getListMyTicketsQueryKey, listMyTickets } from '@/api/generated/endpoints/my-ticket-controller/my-ticket-controller'

const PARAMS = { page: 0, size: 100 }

/** Vé của tôi (`GET /me/tickets`); lấy một trang lớn rồi chia tab ở client vì API chưa lọc theo trạng thái. */
export function useMyTickets() {
  const query = useQuery({
    queryKey: getListMyTicketsQueryKey(PARAMS),
    queryFn: () => listMyTickets(PARAMS),
    retry: false,
  })
  return { ...query, tickets: query.data?.status === 200 ? query.data.data : [] }
}
