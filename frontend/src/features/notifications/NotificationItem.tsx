import { useRouter } from '@tanstack/react-router'
import type { MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'

import type { Notification } from '@/api/generated/model'
import { formatDate, formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'

/**
 * Một thông báo ở popover chuông và trang Thông báo. Chấm đậm là chưa đọc; bấm vào đánh dấu đã đọc rồi mở trang đích
 * (nếu có). Là liên kết thật nên dùng được bằng bàn phím.
 */
export function NotificationItem({
  item,
  compact = false,
  onOpen,
  onMarkRead,
}: {
  item: Notification
  compact?: boolean
  onOpen?: () => void
  onMarkRead: (id: string) => void
}) {
  const { t } = useTranslation()
  const router = useRouter()
  const unread = !item.readAt
  const open = (event?: MouseEvent) => {
    if (unread) onMarkRead(item.id)
    onOpen?.()
    if (item.link) {
      // Đường dẫn trong app do server đặt: đi qua router để không tải lại cả trang.
      event?.preventDefault()
      router.history.push(item.link)
    }
  }
  const body = (
    <>
      <span
        className={cn('mt-1.5 size-2 shrink-0 rounded-full', unread ? 'bg-primary' : 'bg-transparent')}
        role="img"
        aria-label={unread ? t('notifications.unread') : t('notifications.read')}
      />
      <span className="flex min-w-0 flex-col gap-0.5 text-left">
        <span className={cn('text-sm', unread ? 'font-semibold' : 'font-medium')}>{item.title}</span>
        <span className="text-muted-foreground text-[13px]">{item.body}</span>
        <span className="text-muted-foreground text-xs">
          {formatDate(item.createdAt)} · {formatTime(item.createdAt)}
        </span>
      </span>
    </>
  )
  const className = cn('hover:bg-accent flex w-full gap-3 px-4 text-left', compact ? 'py-3' : 'py-4')

  return item.link ? (
    <a href={item.link} className={className} onClick={open}>
      {body}
    </a>
  ) : (
    <button type="button" className={className} onClick={() => open()}>
      {body}
    </button>
  )
}
