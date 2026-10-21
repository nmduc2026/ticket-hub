import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { useListAdministrativeUnits } from '@/api/generated/endpoints/administrative-unit-controller/administrative-unit-controller'
import type { PublicEventSummary } from '@/api/generated/model'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatDateChip, formatTime } from '@/lib/date'
import { formatMoney } from '@/lib/money'

import { EventPoster } from './EventPoster'
import { EventsGrid } from './EventsGrid'
import { useEventSearch } from './useEventSearch'

/** Trang chủ khách (màn #1): tiêu điểm, sắp diễn ra, sắp mở bán, theo thành phố, lời mời cho ban tổ chức. */
export function HomePage() {
  const { t } = useTranslation()
  const events = useEventSearch({ size: 12 })
  const cities = useListAdministrativeUnits({ level: 1 }, { query: { staleTime: 5 * 60_000 } })

  const items = events.page?.items ?? []
  const featured = items.find((event) => event.status === 'ON_SALE') ?? items[0]
  const comingSoon = items.filter((event) => event.status === 'PUBLISHED')
  const cityList = cities.data?.status === 200 ? cities.data.data : []

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-10">
      <h1 className="sr-only">{t('home.discover')}</h1>

      {featured && <FeaturedEvent event={featured} />}

      <section aria-labelledby="home-upcoming" className="flex flex-col gap-4">
        <div className="flex items-end justify-between">
          <h2 id="home-upcoming" className="text-xl font-semibold tracking-tight">
            {t('home.upcoming')}
          </h2>
          <Link to="/events" className="text-primary text-sm font-medium hover:underline">
            {t('home.viewAll')}
          </Link>
        </div>
        <EventsGrid
          page={events.page}
          isLoading={events.isLoading}
          isError={events.isError}
          onRetry={events.refetch}
          emptyTitle={t('home.emptyTitle')}
          emptyBody={t('home.emptyBody')}
        />
      </section>

      {comingSoon.length > 0 && (
        <section aria-labelledby="home-soon" className="flex flex-col gap-4">
          <h2 id="home-soon" className="text-xl font-semibold tracking-tight">
            {t('home.comingSoon')}
          </h2>
          <ul className="grid gap-4 sm:grid-cols-2">
            {comingSoon.map((event) => (
              <li key={event.id}>
                <Link
                  to="/events/$eventId"
                  params={{ eventId: event.id }}
                  className="bg-card hover:bg-accent flex flex-col gap-1 rounded-xl border p-4"
                >
                  <span className="font-semibold">{event.title}</span>
                  <span className="text-muted-foreground text-sm">
                    {formatDateChip(event.startsAt)} · {event.venueName}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {cityList.length > 0 && (
        <section aria-labelledby="home-cities" className="flex flex-col gap-4">
          <h2 id="home-cities" className="text-xl font-semibold tracking-tight">
            {t('home.byCity')}
          </h2>
          <ul className="flex flex-wrap gap-3">
            {cityList.map((city) => (
              <li key={city.id}>
                <Link
                  to="/events"
                  search={{ city: city.id }}
                  className="bg-card hover:bg-accent inline-flex rounded-xl border px-4 py-3 text-sm font-medium"
                >
                  {city.name}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="bg-night text-night-foreground flex flex-col items-start gap-3 rounded-2xl p-8">
        <h2 className="max-w-xl text-2xl font-semibold tracking-tight">{t('home.organizerTitle')}</h2>
        <p className="text-night-muted max-w-xl text-sm">{t('home.organizerBody')}</p>
      </section>
    </div>
  )
}

function FeaturedEvent({ event }: { event: PublicEventSummary }) {
  const { t } = useTranslation()

  return (
    <section
      aria-label={t('home.featuredTag')}
      className="bg-night text-night-foreground grid overflow-hidden rounded-2xl md:grid-cols-2"
    >
      <div className="flex flex-col justify-center gap-3 p-6 md:p-10">
        <Badge variant="secondary" className="self-start">
          {t('home.featuredTag')}
          {event.status !== 'ON_SALE' ? ` · ${t(`events.status.${event.status}`)}` : ''}
        </Badge>
        <h2 className="text-3xl font-semibold tracking-tight">{event.title}</h2>
        <p className="text-night-muted text-sm">
          {formatDateChip(event.startsAt)} · {formatTime(event.startsAt)} · {event.venueName}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-4">
          <Button asChild>
            <Link to="/events/$eventId" params={{ eventId: event.id }}>
              {t('home.pickSeats')}
            </Link>
          </Button>
          {event.minPriceMinor != null && (
            <span className="font-semibold whitespace-nowrap">
              {t('home.from', { price: formatMoney(event.minPriceMinor, event.currency) })}
            </span>
          )}
        </div>
      </div>
      <div className="aspect-[16/10] md:aspect-auto">
        <EventPoster title={event.title} bannerUrl={event.bannerUrl} />
      </div>
    </section>
  )
}
