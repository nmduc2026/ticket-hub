import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import type { PublicEventSummary } from '@/api/generated/model'
import { Badge } from '@/components/ui/badge'
import { formatDateChip, formatTime } from '@/lib/date'
import { formatMoney } from '@/lib/money'

import { EventPoster } from './EventPoster'

/** Thẻ sự kiện (màn #1–2): ảnh 16:10, chip ngày nằm ngang trên ảnh, tên, giờ · địa điểm, giá từ. */
export function EventCard({ event }: { event: PublicEventSummary }) {
  const { t } = useTranslation()
  const showStatus = event.status !== 'ON_SALE'

  return (
    <Link
      to="/events/$eventId"
      params={{ eventId: event.id }}
      className="group bg-card focus-visible:ring-ring flex flex-col overflow-hidden rounded-xl border outline-none focus-visible:ring-2"
    >
      <div className="relative aspect-[16/10] overflow-hidden">
        <EventPoster title={event.title} bannerUrl={event.bannerUrl} className="transition-transform group-hover:scale-[1.02]" />
        <span className="bg-background/95 absolute top-3 left-3 rounded-md px-2 py-1 text-xs font-semibold shadow-sm">
          {formatDateChip(event.startsAt)}
        </span>
        {showStatus && (
          <Badge variant="secondary" className="absolute top-3 right-3">
            {t(`events.status.${event.status}`)}
          </Badge>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-4">
        <h3 className="line-clamp-2 text-base leading-snug font-semibold">{event.title}</h3>
        <p className="text-muted-foreground text-sm">
          {formatTime(event.startsAt)} · {event.venueName}
        </p>
        {event.minPriceMinor != null && (
          <p className="mt-auto pt-2 text-sm font-semibold whitespace-nowrap">
            {t('home.from', { price: formatMoney(event.minPriceMinor, event.currency) })}
          </p>
        )}
      </div>
    </Link>
  )
}
