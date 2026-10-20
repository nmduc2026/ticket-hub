import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api-client'
import { redirectToLogin } from '@/lib/auth'

/**
 * Hết phiên (401) ở bất kỳ query / mutation nào thì chuyển tới đăng nhập. Query chấp nhận khách chưa đăng nhập
 * (vd. `GET /me` ở header) đặt `meta: { optionalAuth: true }`.
 */
function handleUnauthorized(error: unknown, meta: Record<string, unknown> | undefined): void {
  if (error instanceof ApiError && error.status === 401 && !meta?.optionalAuth) {
    redirectToLogin()
  }
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({ onError: (error, query) => handleUnauthorized(error, query.meta) }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => handleUnauthorized(error, mutation.meta),
    }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Lỗi 4xx là kết quả rõ ràng, không thử lại; chỉ thử lại lỗi mạng / 5xx.
        retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 2,
      },
    },
  })
}
