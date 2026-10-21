import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { useGetEvent } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import type { PublicEventDetail } from '@/api/generated/model'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api-client'
import { formatDate, formatEventRange, formatTime } from '@/lib/date'
import { formatMoney } from '@/lib/money'

import { EventPoster } from './EventPoster'

/** Chi tiết sự kiện (màn #3): thông tin, hạng vé và còn vé hay không, giới thiệu, chính sách, nút chọn ghế. */
export function EventDetailPage({ eventId }: { eventId: string }) {
  const { t } = useTranslation()
  const query = useGetEvent(eventId, { query: { staleTime: 15_000, retry: false } })

  if (query.isPending) {
    return <DetailSkeleton />
  }
  if (query.isError || query.data?.status !== 200) {
    const error: unknown = query.error
    const notFound = error instanceof ApiError && error.status === 404
    return (
      <div role="alert" className="mx-auto flex max-w-2xl flex-col items-center gap-3 py-16 text-center">
        <h1 className="text-xl font-semibold">{notFound ? t('eventDetail.notFoundTitle') : t('eventDetail.errorTitle')}</h1>
        {notFound ? (
          <p className="text-muted-foreground text-sm">{t('eventDetail.notFoundBody')}</p>
        ) : (
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        )}
        <Button variant="link" asChild>
          <Link to="/events">{t('eventDetail.backToList')}</Link>
        </Button>
      </div>
    )
  }
  return <EventDetail event={query.data.data} />
}

function EventDetail({ event }: { event: PublicEventDetail }) {
  const { t } = useTranslation()
  const minPrice = event.tiers.length ? Math.min(...event.tiers.map((tier) => tier.priceMinor)) : null
  const currency = event.tiers[0]?.currency ?? 'VND'

  return (
    <article className="mx-auto flex max-w-6xl flex-col gap-8">
      <div className="bg-night text-night-foreground grid overflow-hidden rounded-2xl md:grid-cols-[3fr_2fr]">
        <div className="flex flex-col justify-center gap-3 p-6 md:p-10">
          {event.category && (
            <Badge variant="secondary" className="self-start">
              {event.category}
            </Badge>
          )}
          <h1 className="text-3xl font-semibold tracking-tight">{event.title}</h1>
          <p className="text-night-muted text-sm">{formatEventRange(event.startsAt, event.endsAt)}</p>
          <p className="text-sm font-medium">{event.venueName}</p>
          <p className="text-night-muted text-sm">{event.cityName}</p>
          <PurchasePanel event={event} minPrice={minPrice} currency={currency} />
        </div>
        <div className="aspect-[16/10] md:aspect-auto md:min-h-72">
          <EventPoster title={event.title} bannerUrl={event.bannerUrl} />
        </div>
      </div>

      <div className="grid gap-8 lg:grid-cols-[2fr_1fr]">
        <div className="flex flex-col gap-8">
          {event.description && (
            <section aria-labelledby="detail-about" className="flex flex-col gap-2">
              <h2 id="detail-about" className="text-lg font-semibold">
                {t('eventDetail.about')}
              </h2>
              <p className="text-sm leading-relaxed whitespace-pre-line">{event.description}</p>
            </section>
          )}

          {event.tiers.length > 0 && (
            <section aria-labelledby="detail-tiers" className="flex flex-col gap-3">
              <h2 id="detail-tiers" className="text-lg font-semibold">
                {t('eventDetail.tiers')}
              </h2>
              <ul className="flex flex-col gap-2">
                {event.tiers.map((tier) => (
                  <li key={tier.id} className="bg-card flex items-center justify-between gap-3 rounded-xl border p-4">
                    <div className="flex flex-col">
                      <span className="font-medium">{tier.name}</span>
                      <span className="text-muted-foreground text-sm">
                        {tier.available > 0 ? t('eventDetail.tierAvailable', { count: tier.available }) : t('eventDetail.tierSoldOut')}
                      </span>
                    </div>
                    <span className="font-semibold whitespace-nowrap">{formatMoney(tier.priceMinor, tier.currency)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>

        <aside className="flex flex-col gap-6">
          <section aria-labelledby="detail-policy" className="bg-card flex flex-col gap-2 rounded-xl border p-4">
            <h2 id="detail-policy" className="font-semibold">
              {t('eventDetail.policy')}
            </h2>
            <ul className="text-muted-foreground flex list-disc flex-col gap-1.5 pl-4 text-sm">
              <li>{t('eventDetail.policyMaxTickets', { count: event.maxTicketsPerUser })}</li>
              <li>{t('eventDetail.policyHold')}</li>
              <li>{t('eventDetail.policyRefund')}</li>
              <li>{t('eventDetail.policyCancelled')}</li>
            </ul>
          </section>
          <section aria-labelledby="detail-venue" className="bg-card flex flex-col gap-1 rounded-xl border p-4">
            <h2 id="detail-venue" className="font-semibold">
              {t('eventDetail.venue')}
            </h2>
            <p className="text-sm font-medium">{event.venueName}</p>
            <p className="text-muted-foreground text-sm">{event.cityName}</p>
          </section>
        </aside>
      </div>
    </article>
  )
}

/** Khối giá và nút mua theo trạng thái sự kiện. */
function PurchasePanel({ event, minPrice, currency }: { event: PublicEventDetail; minPrice: number | null; currency: string }) {
  const { t } = useTranslation()

  let action: React.ReactNode
  switch (event.status) {
    case 'ON_SALE':
      action = event.soldOut ? (
        <Button disabled>{t('eventDetail.soldOut')}</Button>
      ) : (
        <Button asChild>
          <Link to="/events/$eventId/seats" params={{ eventId: event.id }}>
            {t('eventDetail.pickSeats')}
          </Link>
        </Button>
      )
      break
    case 'PUBLISHED':
      action = (
        <p role="status" className="text-sm font-medium">
          {t('eventDetail.saleStartsAt', { time: `${formatTime(event.saleStartsAt)} · ${formatDate(event.saleStartsAt)}` })}
        </p>
      )
      break
    case 'SUSPENDED':
      action = (
        <p role="status" className="text-sm">
          <strong>{t('events.status.SUSPENDED')}.</strong> {t('eventDetail.suspendedNote')}
        </p>
      )
      break
    case 'ENDED':
      action = <p role="status" className="text-sm">{t('eventDetail.endedNote')}</p>
      break
    default:
      action = <p role="status" className="text-sm">{t('eventDetail.cancelledNote')}</p>
  }

  return (
    <div className="mt-3 flex flex-wrap items-center gap-4">
      {minPrice != null && (
        <div className="flex flex-col">
          <span className="text-night-muted text-xs">{t('eventDetail.priceFrom')}</span>
          <span className="text-lg font-semibold whitespace-nowrap">{formatMoney(minPrice, currency)}</span>
        </div>
      )}
      {action}
    </div>
  )
}

function DetailSkeleton() {
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6" aria-busy="true">
      <Skeleton className="h-72 w-full rounded-2xl" />
      <Skeleton className="h-5 w-1/2" />
      <Skeleton className="h-5 w-2/3" />
    </div>
  )
}
