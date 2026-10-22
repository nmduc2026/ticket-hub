import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { AlertTriangle, MoreHorizontal } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getListAdminBookingsQueryKey, getListAdminBookingsUrl, useListAdminBookings, useRetryBookingRefund } from '@/api/generated/endpoints/admin-booking-controller/admin-booking-controller'
import { useListAdminOrganizers } from '@/api/generated/endpoints/admin-organizer-controller/admin-organizer-controller'
import type { AdminBookingRow } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'
import { downloadServerCsv } from '@/lib/csv'
import { formatDate, formatTime } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/utils'

import type { AdminOrderSearch } from './adminOrderSearch'
import { OrderPaymentSheet } from './OrderPaymentSheet'

const DEFAULT_PAGE_SIZE = 20
const STATUSES = ['PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED', 'REFUND_FAILED']

/** Đơn hàng & đối soát toàn hệ thống (màn #29): lọc, hoàn thủ công đơn REFUND_FAILED, xuất CSV. */
export function AdminOrdersPage({ search }: { search: AdminOrderSearch }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/admin/orders' })
  const queryClient = useQueryClient()
  const [busyId, setBusyId] = useState<string | null>(null)
  const [viewing, setViewing] = useState<AdminBookingRow | undefined>()

  const page = search.page ?? 0
  const size = search.size ?? DEFAULT_PAGE_SIZE
  const params = { status: search.status, organizerId: search.organizerId, needsReconcile: search.needsReconcile, q: search.q, page, size }
  const bookings = useListAdminBookings(params, { query: { retry: false } })
  const organizers = useListAdminOrganizers({ status: 'APPROVED', size: 100 }, { query: { staleTime: 5 * 60_000 } })
  const retry = useRetryBookingRefund()

  const data = bookings.data?.status === 200 ? bookings.data.data : undefined
  const organizerList = organizers.data?.status === 200 ? organizers.data.data.items : []

  const refresh = () => void queryClient.invalidateQueries({ queryKey: getListAdminBookingsQueryKey() })

  const refundManually = (row: AdminBookingRow) => {
    setBusyId(row.bookingId)
    retry.mutate(
      { bookingId: row.bookingId },
      {
        onSuccess: () => {
          toast.success(t('adminOrders.refundSent'))
          refresh()
        },
        onError: (error) => toast.error(errorMessage(error, t)),
        onSettled: () => setBusyId(null),
      },
    )
  }

  const columns: Column<AdminBookingRow>[] = useMemo(
    () => [
      { id: 'bookingId', header: t('adminOrders.colId'), className: 'font-mono text-xs whitespace-nowrap', cell: (row) => row.bookingId.slice(-8).toUpperCase() },
      { id: 'eventTitle', header: t('adminOrders.colEvent'), cell: (row) => <span className="font-medium">{row.eventTitle}</span> },
      { id: 'organizerName', header: t('adminOrders.colOrganizer'), cell: (row) => row.organizerName },
      { id: 'contactEmail', header: t('adminOrders.colCustomer'), cell: (row) => row.contactEmail },
      { id: 'status', header: t('adminOrders.colStatus'), cell: (row) => t(`myBookings.status.${row.status}`, { defaultValue: row.status }) },
      {
        id: 'needsReconcile',
        header: t('adminOrders.colReconcile'),
        cell: (row) => (row.needsReconcile ? <AlertTriangle className="text-warning size-4" aria-label={t('adminOrders.needsReconcile')} /> : null),
      },
      { id: 'totalMinor', header: t('adminOrders.colTotal'), className: cn('text-right whitespace-nowrap'), cell: (row) => formatMoney(row.totalMinor ?? 0, row.currency ?? 'VND') },
      { id: 'createdAt', header: t('adminOrders.colCreated'), className: 'whitespace-nowrap', cell: (row) => (row.createdAt ? `${formatDate(row.createdAt)} · ${formatTime(row.createdAt)}` : '—') },
      {
        id: 'action',
        header: '',
        cell: (row) =>
          row.status === 'REFUND_FAILED' ? (
            <Button size="sm" onClick={() => refundManually(row)} disabled={busyId === row.bookingId}>
              {t('adminOrders.refund')}
            </Button>
          ) : null,
      },
    ],
    [t, busyId],
  )

  const update = (changes: Partial<AdminOrderSearch>) => void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  const hasFilters = Boolean(search.status?.length || search.organizerId || search.needsReconcile || search.q)
  const resetFilters = () => void navigate({ search: {} })

  const exportRows = () => {
    const url = getListAdminBookingsUrl({ status: search.status, organizerId: search.organizerId, needsReconcile: search.needsReconcile, q: search.q })
    downloadServerCsv(url, 'don-hang.csv').catch((error: unknown) => toast.error(error instanceof ApiError ? error.message : t('list.loadError')))
  }

  const error = bookings.error instanceof ApiError ? bookings.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminOrders.title')}</h1>
      <ListToolbar
        search={{ value: search.q ?? '', onChange: (q) => update({ q: q || undefined }), placeholder: t('adminOrders.searchPlaceholder') }}
        filters={
          <>
            <FacetFilter mode="multi" label={t('adminOrders.colStatus')} options={STATUSES.map((value) => ({ value, label: t(`myBookings.status.${value}`) }))} value={search.status ?? []} onChange={(value) => update({ status: value.length > 0 ? value : undefined })} />
            <FacetFilter mode="single" label={t('adminOrders.colOrganizer')} options={organizerList.map((organizer) => ({ value: organizer.id, label: organizer.displayName }))} value={search.organizerId} onChange={(value) => update({ organizerId: value })} />
            <Button variant={search.needsReconcile ? 'default' : 'outline'} size="sm" onClick={() => update({ needsReconcile: search.needsReconcile ? undefined : true })}>
              {t('adminOrders.needsReconcile')}
            </Button>
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
        emptyTitle={t('adminOrders.empty')}
        actionsHeader={t('adminOrders.sheet.actionsHeader')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('adminOrders.sheet.viewRow')}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => setViewing(row)}>{t('adminOrders.sheet.view')}</DropdownMenuItem>
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
      <OrderPaymentSheet row={viewing} onClose={() => setViewing(undefined)} />
    </div>
  )
}
