import { useGetMe } from '@/api/generated/endpoints/me-controller/me-controller'
import type { MeResponse } from '@/api/generated/model'

export const ME_STALE_TIME = 5 * 60_000

/** Người dùng hiện tại, hoặc `null` khi chưa đăng nhập (khách xem trang công khai không bị đẩy tới đăng nhập). */
export function useCurrentUser(): { user: MeResponse | null; isLoading: boolean } {
  const query = useGetMe({ query: { meta: { optionalAuth: true }, retry: false, staleTime: ME_STALE_TIME } })
  return { user: query.data?.status === 200 ? query.data.data : null, isLoading: query.isPending }
}
