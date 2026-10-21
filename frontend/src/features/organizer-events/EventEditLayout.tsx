import { Link, Outlet, useRouterState } from '@tanstack/react-router'
import { AlertCircle, Check, Circle, ScanLine } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useBreadcrumbTrail } from '@/components/shell/breadcrumb-store'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'

import { type EditTab, type PublishItem, publishChecklist } from './publishChecklist'
import { PublishButton } from './PublishButton'
import { StatusBadge } from './StatusBadge'
import { useOrganizerEvent } from './useOrganizerEvent'

/**
 * Khung sửa sự kiện (màn #13, #24): tên, trạng thái, nút Publish, điều kiện publish và thanh tab. Sự kiện không có
 * hoặc của organizer khác trả 404 (AC-4) thì chỉ hiện "Không tìm thấy", không lộ gì thêm.
 */
export function EventEditLayout({ eventId }: { eventId: string }) {
  const { t } = useTranslation()
  const { event, isLoading, notFound, refetch } = useOrganizerEvent(eventId)
  const location = useRouterState({ select: (state) => state.location })
  const activeTab: EditTab = location.pathname.endsWith('/tiers')
    ? 'tiers'
    : (location.search as { tab?: string }).tab === 'sales'
      ? 'sales'
      : 'info'
  // Breadcrumb 4 cấp: Trang chủ › Sự kiện › <tên> › <tab> (docs/design/README.md §3).
  useBreadcrumbTrail(event ? [{ label: event.title }, { label: t(`organizerEvents.edit.${activeTab}`) }] : [])

  if (isLoading) return <Skeleton className="h-40" aria-busy="true" />
  if (!event) {
    return (
      <StatusPanel
        icon={<AlertCircle />}
        title={notFound ? t('organizerEvents.edit.notFound') : t('organizerEvents.edit.loadError')}
      >
        {!notFound && (
          <button type="button" className="text-primary text-sm underline" onClick={refetch}>
            {t('common.retry')}
          </button>
        )}
        <Link to="/organizer/events" className="text-primary text-sm underline">
          {t('organizerEvents.edit.backToList')}
        </Link>
      </StatusPanel>
    )
  }

  const isDraft = event.status === 'DRAFT'
  const checklist = publishChecklist(event)
  const params = { eventId }

  const tabClass = (tab: EditTab) =>
    cn(
      'border-b-2 px-3 py-2 text-sm font-medium whitespace-nowrap',
      activeTab === tab ? 'border-primary text-foreground' : 'text-muted-foreground hover:text-foreground border-transparent',
    )

  return (
    <div className="flex flex-col gap-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-2xl font-semibold tracking-tight">{event.title}</h1>
            <StatusBadge status={event.status} />
          </div>
          <p className="text-muted-foreground text-sm">
            {[
              event.startsAt && `${formatDate(event.startsAt)} · ${formatTime(event.startsAt)}`,
              event.venueName,
            ]
              .filter(Boolean)
              .join(' · ') || t('organizerEvents.edit.noSchedule')}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {!isDraft && (
            <Button variant="outline" size="sm" asChild>
              <Link to="/checkin/$eventId" params={params}>
                <ScanLine />
                {t('organizerEvents.edit.openCheckin')}
              </Link>
            </Button>
          )}
          {isDraft && <PublishButton event={event} checklist={checklist} />}
        </div>
      </header>

      <nav aria-label={t('organizerEvents.edit.tabs')} className="flex gap-1 overflow-x-auto border-b">
        <Link to="/organizer/events/$eventId/edit" params={params} search={{}} aria-current={activeTab === 'info' ? 'page' : undefined} className={tabClass('info')}>
          {t('organizerEvents.edit.info')}
        </Link>
        <Link to="/organizer/events/$eventId/tiers" params={params} aria-current={activeTab === 'tiers' ? 'page' : undefined} className={tabClass('tiers')}>
          {t('organizerEvents.edit.tiers')}
        </Link>
        <Link to="/organizer/events/$eventId/edit" params={params} search={{ tab: 'sales' }} aria-current={activeTab === 'sales' ? 'page' : undefined} className={tabClass('sales')}>
          {t('organizerEvents.edit.sales')}
        </Link>
      </nav>

      {isDraft ? (
        <div className="grid items-start gap-6 xl:grid-cols-[1fr_18rem]">
          <div className="min-w-0">
            <Outlet />
          </div>
          <Checklist eventId={eventId} entries={checklist} />
        </div>
      ) : (
        <Outlet />
      )}
    </div>
  )
}

function Checklist({ eventId, entries }: { eventId: string; entries: { item: PublishItem; done: boolean; tab: EditTab }[] }) {
  const { t } = useTranslation()
  const missing = entries.filter((entry) => !entry.done).length
  return (
    <aside aria-labelledby="checklist-heading" className="bg-card flex flex-col gap-3 rounded-xl border p-4">
      <div className="flex flex-col gap-0.5">
        <h2 id="checklist-heading" className="text-sm font-semibold">
          {t('organizerEvents.checklist.title')}
        </h2>
        <p className="text-muted-foreground text-xs">
          {missing === 0 ? t('organizerEvents.checklist.ready') : t('organizerEvents.checklist.missing', { count: missing })}
        </p>
      </div>
      <ul className="flex flex-col gap-2">
        {entries.map((entry) => (
          <li key={entry.item} className="flex items-start gap-2 text-sm">
            {entry.done ? (
              <Check className="text-primary mt-0.5 size-4 shrink-0" aria-label={t('organizerEvents.checklist.done')} />
            ) : (
              <Circle className="text-muted-foreground mt-0.5 size-4 shrink-0" aria-label={t('organizerEvents.checklist.todo')} />
            )}
            <span className="flex-1">{t(`organizerEvents.checklist.items.${entry.item}`)}</span>
            {!entry.done && <FixLink eventId={eventId} tab={entry.tab} />}
          </li>
        ))}
      </ul>
    </aside>
  )
}

function FixLink({ eventId, tab }: { eventId: string; tab: EditTab }) {
  const { t } = useTranslation()
  const className = 'text-primary text-xs underline'
  if (tab === 'tiers') {
    return (
      <Link to="/organizer/events/$eventId/tiers" params={{ eventId }} className={className}>
        {t('organizerEvents.checklist.fix')}
      </Link>
    )
  }
  return (
    <Link to="/organizer/events/$eventId/edit" params={{ eventId }} search={tab === 'sales' ? { tab: 'sales' } : {}} className={className}>
      {t('organizerEvents.checklist.fix')}
    </Link>
  )
}
