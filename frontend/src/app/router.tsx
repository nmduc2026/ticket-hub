import type { QueryClient } from '@tanstack/react-query'
import { createRouter, type RouterHistory } from '@tanstack/react-router'

import { captureError } from '@/lib/monitoring'
import { routeTree } from '@/routeTree.gen'

/** `history` chỉ truyền trong test (memory history); chạy thật dùng lịch sử trình duyệt. */
export function createAppRouter(queryClient: QueryClient, history?: RouterHistory) {
  return createRouter({
    routeTree,
    context: { queryClient },
    defaultPreload: 'intent',
    defaultOnCatch: (error) => captureError(error),
    history,
  })
}

declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
