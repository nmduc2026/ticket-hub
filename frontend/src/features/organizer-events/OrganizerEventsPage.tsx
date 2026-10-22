import { Link, useNavigate } from '@tanstack/react-router'
import { MoreHorizontal, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useBulkDuplicateEvents, useDuplicateEvent, useListOrganizerEvents } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import { useListVenues } from '@/api/generated/endpoints/venue-controller/venue-controller'
import type { ListOrganizerEventsStatusItem, OrganizerEventSummary } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'
import { downloadCsv } from '@/lib/csv'
import { formatDate, formatTime } from '@/lib/date'

import { CreateEventDialog } from './CreateEventDialog'
import type { OrganizerEventSearch } from './organizerEventSearch'
import { StatusBadge } from './StatusBadge'

const DEFAULT_PAGE_SIZE = 10
const STATUSES: ListOrganizerEventsStatusItem[] = ['DRAFT', 'PUBLISHED', 'ON_SALE', 'SUSPENDED', 'ENDED', 'CANCELLED']

/** Danh sách sự kiện của organizer (màn #12). Bộ lọc, từ khoá và trang nằm trên URL (AC-12). */
export function OrganizerEventsPage({ search }: { search: OrganizerEventSearch }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/organizer/events/' })
  const [hiddenColumns, setHiddenColumns] = useState<ReadonlySet<string>>(new Set())
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
  const [creating, setCreating] = useState(false)

  const page = search.page ?? 0
  const size = search.size ?? DEFAULT_PAGE_SIZE
  const status = search.status as ListOrganizerEventsStatusItem[] | undefined
  const events = useListOrganizerEvents({ q: search.q, status, venueId: search.venue, page, size })
  const duplicate = useDuplicateEvent()
  const bulkDuplicate = useBulkDuplicateEvents()
  const venues = useListVenues({ query: { staleTime: 5 * 60_000 } })

  const data = events.data?.status === 200 ? events.data.data : undefined
  const venueList = venues.data?.status === 200 ? venues.data.data : []

  const columns: Column<OrganizerEventSummary>[] = useMemo(
    () => [
      {
        id: 'title',
        header: t('organizerEvents.columns.title'),
        cell: (row) => <span className="font-medium">{row.title}</span>,
      },
      {
        id: 'startsAt',
        header: t('organizerEvents.columns.startsAt'),
        className: 'whitespace-nowrap',
        cell: (row) => (row.startsAt ? `${formatDate(row.startsAt)} · ${formatTime(row.startsAt)}` : '—'),
      },
      { id: 'venue', header: t('organizerEvents.columns.venue'), cell: (row) => row.venueName ?? '—' },
      { id: 'status', header: t('organizerEvents.columns.status'), cell: (row) => <StatusBadge status={row.status} /> },
    ],
    [t],
  )

  const update = (changes: Partial<OrganizerEventSearch>) => {
    setSelected(new Set())
    void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  }
  const hasFilters = Boolean(search.q || search.status?.length || search.venue)
  const resetFilters = () => {
    setSelected(new Set())
    void navigate({ search: {} })
  }

  const exportRows = () => {
    const rows = (data?.items ?? []).filter((row) => selected.size === 0 || selected.has(row.id))
    downloadCsv(
      'su-kien.csv',
      columns.map((column) => column.header),
      rows.map((row) => [
        row.title,
        row.startsAt ? `${formatDate(row.startsAt)} ${formatTime(row.startsAt)}` : '',
        row.venueName ?? '',
        t(`organizerEvents.status.${row.status}`),
      ]),
    )
  }

  const onDuplicateError = (failure: unknown) => toast.error(failure instanceof ApiError ? failure.message : t('list.loadError'))

  const duplicateOne = (id: string) =>
    duplicate.mutate(
      { eventId: id },
      {
        onSuccess: (response) => {
          toast.success(t('organizerEvents.duplicated'))
          void navigate({ to: '/organizer/events/$eventId/edit', params: { eventId: response.data.id } })
        },
        onError: onDuplicateError,
      },
    )

  const duplicateSelected = () =>
    bulkDuplicate.mutate(
      { data: { ids: [...selected] } },
      {
        onSuccess: (response) => {
          const results = response.data.results
          const ok = results.filter((item) => item.ok).length
          const failed = results.length - ok
          if (failed > 0) toast.warning(t('organizerEvents.duplicatedBulkPartial', { ok, failed }))
          else toast.success(t('organizerEvents.duplicatedBulk', { ok }))
          setSelected(new Set())
          void events.refetch()
        },
        onError: onDuplicateError,
      },
    )

  const error = events.error instanceof ApiError ? events.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('organizerEvents.title')}</h1>
      <ListToolbar
        search={{
          value: search.q ?? '',
          onChange: (q) => update({ q: q || undefined }),
          placeholder: t('organizerEvents.searchPlaceholder'),
        }}
        filters={
          <>
            <FacetFilter
              mode="multi"
              label={t('organizerEvents.columns.status')}
              options={STATUSES.map((value) => ({ value, label: t(`organizerEvents.status.${value}`) }))}
              value={search.status ?? []}
              onChange={(value) => update({ status: value.length > 0 ? value : undefined })}
            />
            <FacetFilter
              mode="single"
              label={t('organizerEvents.columns.venue')}
              options={venueList.map((venue) => ({ value: venue.id, label: venue.name }))}
              value={search.venue}
              onChange={(value) => update({ venue: value })}
            />
          </>
        }
        hasFilters={hasFilters}
        onReset={resetFilters}
        columns={{
          items: columns.map((column) => ({ id: column.id, label: column.header })),
          hidden: hiddenColumns,
          onToggle: (id) =>
            setHiddenColumns((previous) => {
              const next = new Set(previous)
              if (!next.delete(id)) next.add(id)
              return next
            }),
        }}
        selectedCount={selected.size}
        onExport={exportRows}
        bulkActions={
          selected.size > 0 && (
            <Button size="sm" variant="outline" disabled={bulkDuplicate.isPending} onClick={duplicateSelected}>
              {t('organizerEvents.duplicateSelected', { count: selected.size })}
            </Button>
          )
        }
        primaryAction={
          <Button size="sm" onClick={() => setCreating(true)}>
            <Plus aria-hidden="true" />
            {t('organizerEvents.createButton')}
          </Button>
        }
      />
      <CreateEventDialog open={creating} onOpenChange={setCreating} />
      <DataTable
        columns={columns}
        hiddenColumns={hiddenColumns}
        rows={data?.items}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.title}
        isLoading={events.isPending}
        isError={events.isError}
        errorDetail={
          error && error.status >= 500 && error.traceId
            ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId })
            : undefined
        }
        onRetry={() => void events.refetch()}
        hasFilters={hasFilters}
        onResetFilters={resetFilters}
        emptyTitle={t('organizerEvents.emptyTitle')}
        emptyBody={t('organizerEvents.emptyBody')}
        selection={{ selected, onChange: setSelected }}
        actionsHeader={t('organizerEvents.actions')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.rowMenu', { name: row.title })}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem asChild>
                <Link to="/organizer/events/$eventId/edit" params={{ eventId: row.id }}>
                  {t('organizerEvents.editRow')}
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem disabled={duplicate.isPending} onSelect={() => duplicateOne(row.id)}>
                {t('organizerEvents.duplicateRow')}
              </DropdownMenuItem>
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
    </div>
  )
}
