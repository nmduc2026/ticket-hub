import { useInfiniteQuery } from '@tanstack/react-query'
import { AlertCircle, BellOff, CheckCheck } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { listMyNotifications } from '@/api/generated/endpoints/my-notification-controller/my-notification-controller'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'

import { groupNotifications } from './notificationGroups'
import { NotificationItem } from './NotificationItem'
import { useNotificationActions } from './useNotificationActions'

const PAGE_SIZE = 20

/** Trang Thông báo (màn #20): Tất cả / Chưa đọc, nhóm theo độ mới, tải thêm bằng con trỏ. Hộp thư giữ 90 ngày. */
export function NotificationsPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'all' | 'unread'>('all')
  const actions = useNotificationActions()
  const unreadOnly = tab === 'unread'

  const query = useInfiniteQuery({
    queryKey: ['/api/v1/me/notifications', 'pages', { unreadOnly }],
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }) => {
      const response = await listMyNotifications({ unread: unreadOnly, cursor: pageParam, size: PAGE_SIZE })
      return response.status === 200 ? response.data : { items: [], nextCursor: undefined }
    },
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    retry: false,
  })

  const items = query.data?.pages.flatMap((page) => page.items) ?? []
  const groups = groupNotifications(items)
  const hasUnread = items.some((item) => !item.readAt)

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{t('notifications.title')}</h1>
        <Button variant="outline" size="sm" disabled={!hasUnread || actions.isMarkingAll} onClick={actions.markAllRead}>
          <CheckCheck />
          {t('notifications.markAllRead')}
        </Button>
      </div>

      <Tabs value={tab} onValueChange={(value) => setTab(value === 'unread' ? 'unread' : 'all')}>
        <TabsList>
          <TabsTrigger value="all">{t('notifications.tabAll')}</TabsTrigger>
          <TabsTrigger value="unread">{t('notifications.tabUnread')}</TabsTrigger>
        </TabsList>
      </Tabs>

      {query.isPending ? (
        <Skeleton className="h-64 w-full" aria-busy="true" />
      ) : query.isError ? (
        <StatusPanel icon={<AlertCircle />} title={t('notifications.loadError')}>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        </StatusPanel>
      ) : groups.length === 0 ? (
        <StatusPanel icon={<BellOff />} title={unreadOnly ? t('notifications.emptyUnread') : t('notifications.empty')} />
      ) : (
        groups.map(({ group, items: groupItems }) => (
          <section key={group} className="flex flex-col gap-2">
            <h2 className="text-muted-foreground text-[13px] font-medium">{t(`notifications.group.${group}`)}</h2>
            <ul className="bg-card divide-y overflow-hidden rounded-xl border">
              {groupItems.map((item) => (
                <li key={item.id}>
                  <NotificationItem item={item} onMarkRead={actions.markRead} />
                </li>
              ))}
            </ul>
          </section>
        ))
      )}

      {query.hasNextPage && (
        <Button variant="outline" className="self-center" disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
          {t('notifications.loadOlder')}
        </Button>
      )}
      <p className="text-muted-foreground text-center text-xs">{t('notifications.retention')}</p>
    </div>
  )
}
