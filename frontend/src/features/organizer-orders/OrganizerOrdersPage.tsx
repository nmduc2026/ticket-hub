import { useNavigate } from '@tanstack/react-router'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useListOrganizerEvents } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import { getListOrganizerBookingsUrl, useListOrganizerBookings } from '@/api/generated/endpoints/organizer-sales-controller/organizer-sales-controller'
import type { OrganizerBooking } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { DateRangeFilter } from '@/components/list/DateRangeFilter'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { ApiError } from '@/lib/api-client'
import { downloadServerCsv } from '@/lib/csv'
import { formatDate, formatTime, vnInputsToIso } from '@/lib/date'
import { formatMoney } from '@/lib/money'

import type { OrganizerOrderSearch } from './organizerOrderSearch'

const DEFAULT_PAGE_SIZE = 20
const STATUSES = ['PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED', 'REFUND_FAILED']

/** Đơn hàng của organizer (màn #14): lọc theo sự kiện / trạng thái / khoảng ngày / từ khoá, xuất CSV theo bộ lọc. */
export function OrganizerOrdersPage({ search }: { search: OrganizerOrderSearch }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/organizer/orders' })

  const page = search.page ?? 0
  const size = search.size ?? DEFAULT_PAGE_SIZE
  const from = vnInputsToIso(search.from ?? '', '00:00')
  const to = search.to ? exclusiveEndIso(search.to) : undefined

  const bookings = useListOrganizerBookings(
    { eventId: search.eventId, status: search.status, from, to, q: search.q, page, size },
    { query: { retry: false } },
  )
  const events = useListOrganizerEvents({ size: 100 }, { query: { staleTime: 5 * 60_000 } })

  const data = bookings.data?.status === 200 ? bookings.data.data : undefined
  const eventList = events.data?.status === 200 ? events.data.data.items : []

  const columns: Column<OrganizerBooking>[] = useMemo(
    () => [
      { id: 'bookingId', header: t('organizerOrders.colId'), className: 'font-mono text-xs whitespace-nowrap', cell: (row) => row.bookingId.slice(-8).toUpperCase() },
      { id: 'eventTitle', header: t('organizerOrders.colEvent'), cell: (row) => <span className="font-medium">{row.eventTitle}</span> },
      { id: 'contactEmail', header: t('organizerOrders.colCustomer'), cell: (row) => row.contactEmail },
      { id: 'ticketCount', header: t('organizerOrders.colTickets'), cell: (row) => row.ticketCount },
      { id: 'status', header: t('organizerOrders.colStatus'), cell: (row) => t(`myBookings.status.${row.status}`, { defaultValue: row.status }) },
      { id: 'createdAt', header: t('organizerOrders.colCreated'), className: 'whitespace-nowrap', cell: (row) => `${formatDate(row.createdAt)} · ${formatTime(row.createdAt)}` },
      { id: 'totalMinor', header: t('organizerOrders.colTotal'), className: 'text-right whitespace-nowrap', cell: (row) => formatMoney(row.totalMinor, row.currency) },
    ],
    [t],
  )

  const update = (changes: Partial<OrganizerOrderSearch>) => void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  const hasFilters = Boolean(search.eventId || search.status?.length || search.from || search.to || search.q)
  const resetFilters = () => void navigate({ search: {} })

  const exportRows = () => {
    const url = getListOrganizerBookingsUrl({ eventId: search.eventId, status: search.status, from, to, q: search.q })
    downloadServerCsv(url, 'don-hang.csv').catch((error: unknown) => {
      toast.error(error instanceof ApiError ? error.message : t('list.loadError'))
    })
  }

  const error = bookings.error instanceof ApiError ? bookings.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('organizerOrders.title')}</h1>
      <ListToolbar
        search={{ value: search.q ?? '', onChange: (q) => update({ q: q || undefined }), placeholder: t('organizerOrders.searchPlaceholder') }}
        filters={
          <>
            <FacetFilter
              mode="single"
              label={t('organizerOrders.colEvent')}
              options={eventList.map((event) => ({ value: event.id, label: event.title }))}
              value={search.eventId}
              onChange={(value) => update({ eventId: value })}
            />
            <FacetFilter
              mode="multi"
              label={t('organizerOrders.colStatus')}
              options={STATUSES.map((value) => ({ value, label: t(`myBookings.status.${value}`) }))}
              value={search.status ?? []}
              onChange={(value) => update({ status: value.length > 0 ? value : undefined })}
            />
            <DateRangeFilter label={t('organizerOrders.colCreated')} value={{ from: search.from, to: search.to }} onChange={(value) => update({ from: value.from, to: value.to })} />
          </>
        }
        hasFilters={hasFilters}
        onReset={resetFilters}
        selectedCount={0}
        onExport={exportRows}
      />
      <DataTable
        columns={columns}
        rows={data?.items}
        getRowId={(row) => row.bookingId}
        getRowLabel={(row) => row.bookingId}
        isLoading={bookings.isPending}
        isError={bookings.isError}
        errorDetail={error && error.status >= 500 && error.traceId ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId }) : undefined}
        onRetry={() => void bookings.refetch()}
        hasFilters={hasFilters}
        onResetFilters={resetFilters}
        emptyTitle={t('organizerOrders.emptyTitle')}
        emptyBody={t('organizerOrders.emptyBody')}
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

/** "2026-11-15" → ISO UTC của 00:00 giờ Việt Nam ngày kế tiếp (mốc trên loại trừ, khớp quy ước BE). */
function exclusiveEndIso(date: string): string | undefined {
  const [year, month, day] = date.split('-').map(Number)
  if (![year, month, day].every(Number.isInteger)) return undefined
  const next = new Date(Date.UTC(year, month - 1, day + 1))
  const nextDate = `${next.getUTCFullYear()}-${String(next.getUTCMonth() + 1).padStart(2, '0')}-${String(next.getUTCDate()).padStart(2, '0')}`
  return vnInputsToIso(nextDate, '00:00')
}
