import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { Bell } from 'lucide-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  getGetMyUnreadNotificationCountQueryKey,
  getListMyNotificationsQueryKey,
  getMyUnreadNotificationCount,
  listMyNotifications,
} from '@/api/generated/endpoints/my-notification-controller/my-notification-controller'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useCurrentUser } from '@/features/auth/useCurrentUser'
import { NotificationItem } from '@/features/notifications/NotificationItem'
import { useNotificationActions } from '@/features/notifications/useNotificationActions'

const POLL_INTERVAL_MS = 60_000
const PREVIEW_SIZE = 5
const PREVIEW_PARAMS = { size: PREVIEW_SIZE }

/**
 * Chuông thông báo ở khung của cả 3 vai trò (màn #20). Số chưa đọc polling 60 giây cho tới khi có SSE; popover liệt
 * kê vài thông báo mới nhất, có "Đánh dấu tất cả đã đọc" và lối tới trang Thông báo. Khách chưa đăng nhập không có
 * hộp thư nên không hiện chuông. Popover đóng khi bấm ra ngoài hoặc nhấn Esc, và luôn trả focus về nút chuông.
 */
export function NotificationBell() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const trigger = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(false)
  const actions = useNotificationActions()
  const enabled = user !== null

  const unread = useQuery({
    queryKey: getGetMyUnreadNotificationCountQueryKey(),
    queryFn: () => getMyUnreadNotificationCount(),
    enabled,
    refetchInterval: POLL_INTERVAL_MS,
    retry: false,
  })
  const preview = useQuery({
    queryKey: getListMyNotificationsQueryKey(PREVIEW_PARAMS),
    queryFn: () => listMyNotifications(PREVIEW_PARAMS),
    enabled: enabled && open,
    retry: false,
  })

  if (!enabled) return null

  const count = unread.data?.status === 200 ? unread.data.data.count : 0
  const items = preview.data?.status === 200 ? preview.data.data.items : []
  const label = count > 0 ? t('notifications.bellUnread', { count }) : t('notifications.title')

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button ref={trigger} variant="ghost" size="icon" className="relative" aria-label={label}>
          <Bell />
          {count > 0 && (
            <span
              data-testid="bell-count"
              className="bg-destructive absolute -top-0.5 -right-0.5 flex min-w-4 items-center justify-center rounded-full px-1 text-[10px] leading-4 font-semibold text-white"
              aria-hidden="true"
            >
              {count > 99 ? '99+' : count}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        className="w-[min(24rem,calc(100vw-1rem))] p-0"
        onCloseAutoFocus={(event) => {
          // Radix không trả focus khi đóng bằng click ra ngoài; thiết kế yêu cầu luôn trả về nút mở.
          event.preventDefault()
          trigger.current?.focus()
        }}
      >
        <div className="flex items-center justify-between gap-2 border-b px-4 py-3">
          <span className="text-sm font-semibold">{t('notifications.title')}</span>
          <Button variant="link" size="sm" className="h-auto p-0 text-[13px]" disabled={count === 0 || actions.isMarkingAll} onClick={actions.markAllRead}>
            {t('notifications.markAllRead')}
          </Button>
        </div>
        {items.length === 0 ? (
          <p className="text-muted-foreground px-4 py-8 text-center text-sm">
            {preview.isPending ? t('common.loading') : t('notifications.empty')}
          </p>
        ) : (
          <ul className="max-h-96 overflow-y-auto">
            {items.map((item) => (
              <li key={item.id}>
                <NotificationItem item={item} compact onOpen={() => setOpen(false)} onMarkRead={actions.markRead} />
              </li>
            ))}
          </ul>
        )}
        <div className="border-t p-2">
          <Button variant="ghost" size="sm" className="w-full" asChild>
            <Link to="/me/notifications" onClick={() => setOpen(false)}>
              {t('notifications.viewAll')}
            </Link>
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
