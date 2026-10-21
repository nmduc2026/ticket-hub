import type { Notification } from '@/api/generated/model'
import { DISPLAY_TIME_ZONE } from '@/lib/date'

export type NotificationGroup = 'today' | 'week' | 'older'

const DAY_MS = 86_400_000

/** Ngày theo giờ Việt Nam (yyyy-MM-dd), để "Hôm nay" đúng theo lịch của người dùng chứ không theo UTC. */
function vnDay(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: DISPLAY_TIME_ZONE }).format(date)
}

/** Nhóm theo độ mới (màn #20): hôm nay, trong 7 ngày qua, cũ hơn. */
export function notificationGroup(createdAt: string, now: Date = new Date()): NotificationGroup {
  const created = new Date(createdAt)
  if (vnDay(created) === vnDay(now)) return 'today'
  return now.getTime() - created.getTime() < 7 * DAY_MS ? 'week' : 'older'
}

/** Chia danh sách (đã mới nhất trước) thành các nhóm có thứ tự cố định, bỏ nhóm rỗng. */
export function groupNotifications(
  items: readonly Notification[],
  now: Date = new Date(),
): Array<{ group: NotificationGroup; items: Notification[] }> {
  const order: NotificationGroup[] = ['today', 'week', 'older']
  return order
    .map((group) => ({ group, items: items.filter((item) => notificationGroup(item.createdAt, now) === group) }))
    .filter((entry) => entry.items.length > 0)
}
