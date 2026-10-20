import type { QueryClient } from '@tanstack/react-query'
import { redirect } from '@tanstack/react-router'

import { getGetMeQueryOptions } from '@/api/generated/endpoints/me-controller/me-controller'
import type { MeResponse } from '@/api/generated/model'
import { ApiError } from '@/lib/api-client'
import { redirectToLogin } from '@/lib/auth'

import { ME_STALE_TIME } from './useCurrentUser'
import { hasRole, type Role } from './roles'

/** `GET /me` qua cache của Query; `null` khi chưa đăng nhập. */
export async function loadCurrentUser(queryClient: QueryClient): Promise<MeResponse | null> {
  try {
    const response = await queryClient.ensureQueryData(
      getGetMeQueryOptions({ query: { staleTime: ME_STALE_TIME, retry: false } }),
    )
    return response.status === 200 ? response.data : null
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

/**
 * Dùng trong `beforeLoad` của trang chỉ cần đăng nhập: chưa đăng nhập → tới Keycloak rồi quay lại `href`.
 */
export async function requireUser(queryClient: QueryClient, href: string): Promise<MeResponse> {
  const user = await loadCurrentUser(queryClient)
  if (user === null) {
    redirectToLogin(href)
    // Trình duyệt đang rời trang; giữ route ở trạng thái chờ thay vì render lỗi.
    return new Promise<MeResponse>(() => {})
  }
  return user
}

/**
 * Như {@link requireUser} và thêm yêu cầu role; đã đăng nhập nhưng thiếu role → `/forbidden`. Chặn ở giao diện chỉ
 * là UX, quyền thật do backend kiểm (overview.md §13.3).
 */
export async function requireRole(queryClient: QueryClient, role: Role, href: string): Promise<MeResponse> {
  const user = await requireUser(queryClient, href)
  if (!hasRole(user.roles, role)) {
    throw redirect({ to: '/forbidden' })
  }
  return user
}
