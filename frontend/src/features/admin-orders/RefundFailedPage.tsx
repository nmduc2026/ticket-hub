import { useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  getListAdminBookingsQueryKey,
  useListAdminBookings,
  useRetryBookingRefund,
} from '@/api/generated/endpoints/admin-booking-controller/admin-booking-controller'
import type { AdminBooking } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatTime } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'
import { formatMoney } from '@/lib/money'

const PAGE_SIZE = 20

/** Đơn hoàn tiền lỗi cần Admin hoàn thủ công (màn #29, phần hoàn). Vé của các đơn này vẫn đã bị thu hồi. */
export function RefundFailedPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [busyId, setBusyId] = useState<string | null>(null)
  const params = { status: 'REFUND_FAILED', page, size: PAGE_SIZE } as const
  const query = useListAdminBookings(params, { query: { retry: false } })
  const retry = useRetryBookingRefund()

  if (query.isPending) return <Skeleton className="h-80 w-full" aria-busy="true" />
  if (query.isError) {
    return (
      <StatusPanel icon={<AlertCircle />} title={t('adminOrders.loadError')}>
        <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
          {t('common.retry')}
        </Button>
      </StatusPanel>
    )
  }
  const rows: AdminBooking[] = query.data.status === 200 ? query.data.data : []

  const refundManually = (booking: AdminBooking) => {
    setBusyId(booking.bookingId)
    retry.mutate(
      { bookingId: booking.bookingId },
      {
        onSuccess: () => {
          toast.success(t('adminOrders.refundSent'))
          void queryClient.invalidateQueries({ queryKey: getListAdminBookingsQueryKey() })
        },
        onError: (error) => toast.error(errorMessage(error, t)),
        onSettled: () => setBusyId(null),
      },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t('adminOrders.title')}</h1>
        <p className="text-muted-foreground text-sm">{t('adminOrders.subtitle')}</p>
      </div>

      {rows.length === 0 ? (
        <StatusPanel icon={<CheckCircle2 />} title={t('adminOrders.empty')} />
      ) : (
        <div className="bg-card overflow-x-auto rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('adminOrders.colId')}</TableHead>
                <TableHead>{t('adminOrders.colCustomer')}</TableHead>
                <TableHead>{t('adminOrders.colEvent')}</TableHead>
                <TableHead className="text-right">{t('adminOrders.colTotal')}</TableHead>
                <TableHead>{t('adminOrders.colFailedAt')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((booking) => (
                <TableRow key={booking.bookingId}>
                  <TableCell className="font-mono text-xs whitespace-nowrap">{booking.bookingId.slice(-8).toUpperCase()}</TableCell>
                  <TableCell>{booking.userEmail}</TableCell>
                  <TableCell className="font-medium">{booking.eventTitle}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">{formatMoney(booking.totalMinor, booking.currency)}</TableCell>
                  <TableCell className="whitespace-nowrap">
                    {booking.failedAt ? `${formatDate(booking.failedAt)} · ${formatTime(booking.failedAt)}` : '—'}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" onClick={() => refundManually(booking)} disabled={busyId === booking.bookingId}>
                      {t('adminOrders.refund')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <nav className="flex items-center justify-between text-sm" aria-label={t('adminOrders.paging')}>
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
          {t('tickets.prev')}
        </Button>
        <span>{t('myBookings.page', { page: page + 1 })}</span>
        <Button variant="outline" size="sm" disabled={rows.length < PAGE_SIZE} onClick={() => setPage(page + 1)}>
          {t('tickets.next')}
        </Button>
      </nav>
    </div>
  )
}
