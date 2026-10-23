import { useNavigate } from '@tanstack/react-router'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { useListAdministrativeUnits } from '@/api/generated/endpoints/administrative-unit-controller/administrative-unit-controller'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import { EVENT_CATEGORIES } from './categories'
import { EventsGrid } from './EventsGrid'
import type { EventSearchParams } from './eventSearchParams'
import { type EventTimeFilter, timeRangeFor } from './timeRange'
import { useEventSearch } from './useEventSearch'

const PAGE_SIZE = 12
const ALL = '__all__'

/** Danh sách và tìm kiếm sự kiện (màn #2). Bộ lọc nằm trên URL: đổi bộ lọc thì về trang đầu. */
export function EventListPage({ search }: { search: EventSearchParams }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/events/' })
  const page = search.page ?? 0
  const range = useMemo(() => timeRangeFor(search.when, new Date()), [search.when])

  const events = useEventSearch({
    q: search.q,
    cityId: search.city,
    category: search.category,
    from: range.from,
    to: range.to,
    page,
    size: PAGE_SIZE,
  })
  const cities = useListAdministrativeUnits({ level: 1 }, { query: { staleTime: 5 * 60_000 } })
  const cityList = cities.data?.status === 200 ? cities.data.data : []

  const update = (changes: Partial<EventSearchParams>) =>
    void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  const hasFilters = Boolean(search.q || search.city || search.category || (search.when && search.when !== 'all'))
  const total = events.page?.total ?? 0
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-[16rem_1fr]">
      <aside aria-label={t('events.filters')} className="bg-card flex h-fit flex-col gap-5 rounded-xl border p-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold">{t('events.filters')}</h2>
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={() => void navigate({ search: {} })}>
              {t('events.reset')}
            </Button>
          )}
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium">{t('events.categories')}</legend>
          <RadioGroup
            value={search.category ?? ALL}
            onValueChange={(value) => update({ category: value === ALL ? undefined : value })}
          >
            <CategoryOption value={ALL} label={t('events.allCategories')} />
            {EVENT_CATEGORIES.map((category) => (
              <CategoryOption key={category} value={category} label={category} />
            ))}
          </RadioGroup>
        </fieldset>

        <div className="flex flex-col gap-2">
          <Label htmlFor="filter-city">{t('events.city')}</Label>
          <Select value={search.city ?? ALL} onValueChange={(value) => update({ city: value === ALL ? undefined : value })}>
            <SelectTrigger id="filter-city" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('events.allCities')}</SelectItem>
              {cityList.map((city) => (
                <SelectItem key={city.id} value={city.id}>
                  {city.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="filter-when">{t('events.when')}</Label>
          <Select
            value={search.when ?? 'all'}
            onValueChange={(value) => update({ when: value === 'all' ? undefined : (value as EventTimeFilter) })}
          >
            <SelectTrigger id="filter-when" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(['all', 'today', 'weekend', 'month'] as const).map((option) => (
                <SelectItem key={option} value={option}>
                  {t(`events.whenOptions.${option}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </aside>

      <section className="flex flex-col gap-4" aria-live="polite">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h1 className="text-2xl font-semibold tracking-tight">
            {search.category ?? t('events.title')}
            {search.q ? ` · “${search.q}”` : ''}
          </h1>
          {events.page && <p className="text-muted-foreground text-sm">{t('events.results', { count: total })}</p>}
        </div>

        <EventsGrid page={events.page} isLoading={events.isLoading} isError={events.isError} onRetry={events.refetch} />

        {pageCount > 1 && (
          <nav className="flex items-center justify-center gap-3" aria-label={t('events.title')}>
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => void navigate({ search: (previous) => ({ ...previous, page: page - 1 || undefined }) })}
            >
              {t('events.prev')}
            </Button>
            <span className="text-sm">{t('events.page', { page: page + 1 })}</span>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= pageCount}
              onClick={() => void navigate({ search: (previous) => ({ ...previous, page: page + 1 }) })}
            >
              {t('events.next')}
            </Button>
          </nav>
        )}
      </section>
    </div>
  )
}

function CategoryOption({ value, label }: { value: string; label: string }) {
  const id = `category-${value}`
  return (
    <div className="flex items-center gap-2">
      <RadioGroupItem value={value} id={id} />
      <Label htmlFor={id} className="font-normal">
        {label}
      </Label>
    </div>
  )
}
