import { useTranslation } from 'react-i18next'

import type { PublicEventPage } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

import { EventCard } from './EventCard'

interface EventsGridProps {
  page: PublicEventPage | null
  isLoading: boolean
  isError: boolean
  onRetry: () => void
  /** Chữ hiển thị khi danh sách trống; mặc định là "không có sự kiện phù hợp". */
  emptyTitle?: string
  emptyBody?: string
  skeletonCount?: number
}

/** Lưới thẻ sự kiện kèm đủ trạng thái: đang tải (skeleton), lỗi có thử lại, trống. */
export function EventsGrid({
  page,
  isLoading,
  isError,
  onRetry,
  emptyTitle,
  emptyBody,
  skeletonCount = 6,
}: EventsGridProps) {
  const { t } = useTranslation()

  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true">
        {Array.from({ length: skeletonCount }, (_, index) => (
          <div key={index} className="flex flex-col gap-3">
            <Skeleton className="aspect-[16/10] w-full rounded-xl" />
            <Skeleton className="h-5 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        ))}
      </div>
    )
  }

  if (isError || !page) {
    return (
      <div role="alert" className="flex flex-col items-center gap-3 rounded-xl border border-dashed py-12 text-center">
        <p className="font-medium">{t('events.errorTitle')}</p>
        <Button variant="outline" size="sm" onClick={onRetry}>
          {t('common.retry')}
        </Button>
      </div>
    )
  }

  if (page.items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-1 rounded-xl border border-dashed py-12 text-center">
        <p className="font-medium">{emptyTitle ?? t('events.noResultsTitle')}</p>
        <p className="text-muted-foreground text-sm">{emptyBody ?? t('events.noResultsBody')}</p>
      </div>
    )
  }

  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {page.items.map((event) => (
        <li key={event.id} className="flex">
          <div className="flex w-full flex-col">
            <EventCard event={event} />
          </div>
        </li>
      ))}
    </ul>
  )
}
