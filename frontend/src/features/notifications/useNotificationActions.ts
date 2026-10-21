import { useQueryClient } from '@tanstack/react-query'

import {
  getGetMyUnreadNotificationCountQueryKey,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
} from '@/api/generated/endpoints/my-notification-controller/my-notification-controller'

/** Số chưa đọc và hộp thư cùng đổi sau khi đánh dấu đã đọc: tải lại đúng hai nhóm query đó. */
export function useNotificationActions() {
  const queryClient = useQueryClient()
  // Khoá của số chưa đọc là một phần tử riêng, không phải tiền tố của khoá danh sách: tải lại cả hai.
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['/api/v1/me/notifications'] }),
      queryClient.invalidateQueries({ queryKey: getGetMyUnreadNotificationCountQueryKey() }),
    ])

  const markOne = useMarkNotificationRead({ mutation: { onSuccess: refresh } })
  const markAll = useMarkAllNotificationsRead({ mutation: { onSuccess: refresh } })

  return {
    markRead: (notificationId: string) => markOne.mutate({ notificationId }),
    markAllRead: () => markAll.mutate(),
    isMarkingAll: markAll.isPending,
  }
}
