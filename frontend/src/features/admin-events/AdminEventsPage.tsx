import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { MoreHorizontal } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getListAdminEventsQueryKey, useListAdminEvents, useResumeEvent } from '@/api/generated/endpoints/admin-event-controller/admin-event-controller'
import { useListAdminOrganizers } from '@/api/generated/endpoints/admin-organizer-controller/admin-organizer-controller'
import type { AdminEventSummary, AdminEventSummaryStatus } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { DateRangeFilter } from '@/components/list/DateRangeFilter'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'
import { formatDate, formatTime, vnInputsToIso } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

import type { AdminEventSearch } from './adminEventSearch'
import { CancelEventDialog } from './CancelEventDialog'
import { RefundProgressCell } from './RefundProgressCell'
import { SuspendEventDialog } from './SuspendEventDialog'

const DEFAULT_PAGE_SIZE = 20
const STATUSES: AdminEventSummaryStatus[] = ['DRAFT', 'PUBLISHED', 'ON_SALE', 'SUSPENDED', 'ENDED', 'CANCELLED']

/** Sự kiện toàn hệ thống của Admin (màn #34): lọc, tạm dừng / mở lại / huỷ, tạm dừng hàng loạt, tiến độ hoàn. */
export function AdminEventsPage({ search }: { search: AdminEventSearch }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/admin/events' })
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
  const [suspending, setSuspending] = useState<{ ids: string[]; title?: string } | undefined>()
  const [cancelling, setCancelling] = useState<{ id: string; title: string } | undefined>()
  const resume = useResumeEvent()

  const page = search.page ?? 0
  const size = search.size ?? DEFAULT_PAGE_SIZE
  const from = vnInputsToIso(search.from ?? '', '00:00')
  const to = search.to ? vnInputsToIso(search.to, '00:00') : undefined
  const events = useListAdminEvents({ status: search.status as AdminEventSummaryStatus | undefined, organizerId: search.organizerId, from, to, q: search.q, page, size })
  const organizers = useListAdminOrganizers({ status: 'APPROVED', size: 100 }, { query: { staleTime: 5 * 60_000 } })

  const data = events.data?.status === 200 ? events.data.data : undefined
  const organizerList = organizers.data?.status === 200 ? organizers.data.data.items : []

  const refresh = () => {
    setSelected(new Set())
    void queryClient.invalidateQueries({ queryKey: getListAdminEventsQueryKey() })
  }

  const columns: Column<AdminEventSummary>[] = useMemo(
    () => [
      { id: 'title', header: t('adminEvents.colTitle'), cell: (row) => <span className="font-medium">{row.title}</span> },
      { id: 'organizer', header: t('adminEvents.colOrganizer'), cell: (row) => row.organizerName },
      {
        id: 'startsAt',
        header: t('adminEvents.colStartsAt'),
        className: 'whitespace-nowrap',
        cell: (row) => (row.startsAt ? `${formatDate(row.startsAt)} · ${formatTime(row.startsAt)}` : '—'),
      },
      { id: 'status', header: t('adminEvents.colStatus'), cell: (row) => t(`organizerEvents.status.${row.status}`) },
      {
        id: 'refundProgress',
        header: t('adminEvents.colRefundProgress'),
        cell: (row) => (row.status === 'CANCELLED' ? <RefundProgressCell eventId={row.id} /> : <span className="text-muted-foreground">—</span>),
      },
    ],
    [t],
  )

  const update = (changes: Partial<AdminEventSearch>) => {
    setSelected(new Set())
    void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  }
  const hasFilters = Boolean(search.status || search.organizerId || search.from || search.to || search.q)
  const resetFilters = () => {
    setSelected(new Set())
    void navigate({ search: {} })
  }

  const resumeOne = (row: AdminEventSummary) =>
    resume.mutate({ id: row.id }, { onSuccess: () => { toast.success(t('adminEvents.resumed')); refresh() }, onError: (error) => toast.error(errorMessage(error, t)) })

  const error = events.error instanceof ApiError ? events.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminEvents.title')}</h1>
      <ListToolbar
        search={{ value: search.q ?? '', onChange: (q) => update({ q: q || undefined }), placeholder: t('adminEvents.searchPlaceholder') }}
        filters={
          <>
            <FacetFilter mode="single" label={t('adminEvents.colStatus')} options={STATUSES.map((value) => ({ value, label: t(`organizerEvents.status.${value}`) }))} value={search.status} onChange={(value) => update({ status: value })} />
            <FacetFilter mode="single" label={t('adminEvents.colOrganizer')} options={organizerList.map((organizer) => ({ value: organizer.id, label: organizer.displayName }))} value={search.organizerId} onChange={(value) => update({ organizerId: value })} />
            <DateRangeFilter label={t('adminEvents.colStartsAt')} value={{ from: search.from, to: search.to }} onChange={(value) => update({ from: value.from, to: value.to })} />
          </>
        }
        hasFilters={hasFilters}
        onReset={resetFilters}
        selectedCount={selected.size}
        bulkActions={
          selected.size > 0 && (
            <Button size="sm" variant="destructive" onClick={() => setSuspending({ ids: [...selected] })}>
              {t('adminEvents.suspendSelected', { count: selected.size })}
            </Button>
          )
        }
      />
      <DataTable
        columns={columns}
        rows={data?.items}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.title}
        isLoading={events.isPending}
        isError={events.isError}
        errorDetail={error && error.status >= 500 && error.traceId ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId }) : undefined}
        onRetry={() => void events.refetch()}
        hasFilters={hasFilters}
        onResetFilters={resetFilters}
        emptyTitle={t('adminEvents.emptyTitle')}
        selection={{ selected, onChange: setSelected }}
        actionsHeader={t('adminEvents.actions')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.rowMenu', { name: row.title })}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {(row.status === 'PUBLISHED' || row.status === 'ON_SALE') && (
                <DropdownMenuItem onSelect={() => setSuspending({ ids: [row.id], title: row.title })}>{t('adminEvents.suspend')}</DropdownMenuItem>
              )}
              {row.status === 'SUSPENDED' && <DropdownMenuItem onSelect={() => resumeOne(row)}>{t('adminEvents.resume')}</DropdownMenuItem>}
              {(row.status === 'PUBLISHED' || row.status === 'ON_SALE' || row.status === 'SUSPENDED') && (
                <DropdownMenuItem variant="destructive" onSelect={() => setCancelling({ id: row.id, title: row.title })}>
                  {t('adminEvents.cancel')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
        pagination={{
          page,
          size,
          total: data?.total ?? 0,
          onPageChange: (next) => void navigate({ search: (previous) => ({ ...previous, page: next || undefined }) }),
          onSizeChange: (next) => update({ size: next === DEFAULT_PAGE_SIZE ? undefined : next }),
        }}
      />
      <SuspendEventDialog target={suspending} onClose={() => setSuspending(undefined)} onDone={refresh} />
      <CancelEventDialog target={cancelling} onClose={() => setCancelling(undefined)} onDone={refresh} />
    </div>
  )
}
