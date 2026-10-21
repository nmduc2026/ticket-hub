import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { AlertCircle, Receipt } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { getListMyBookingsQueryKey, listMyBookings } from '@/api/generated/endpoints/my-booking-controller/my-booking-controller'
import type { BookingSummary } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatDateChip, formatTime } from '@/lib/date'
import { formatMoney } from '@/lib/money'

import { canRefund, RefundDialog } from './RefundDialog'

const PAGE_SIZE = 10
const ALL = 'ALL'
const STATUSES = ['PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED', 'REFUND_FAILED'] as const

/** Đơn hàng của tôi (#21): phân trang theo server; ô tìm và lọc trạng thái chỉ áp lên trang đang xem (docs/design/checkout.md §5). */
export function MyBookingsPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [needle, setNeedle] = useState('')
  const [status, setStatus] = useState('')
  const [refunding, setRefunding] = useState<BookingSummary | null>(null)
  const params = { page, size: PAGE_SIZE }
  const query = useQuery({
    queryKey: getListMyBookingsQueryKey(params),
    queryFn: () => listMyBookings(params),
    retry: false,
  })

  if (query.isPending) return <Skeleton className="mx-auto h-80 max-w-5xl" aria-busy="true" />
  if (query.isError) {
    return (
      <StatusPanel icon={<AlertCircle />} title={t('myBookings.loadError')}>
        <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
          {t('common.retry')}
        </Button>
      </StatusPanel>
    )
  }

  const bookings = query.data.status === 200 ? query.data.data : []
  const text = needle.trim().toLowerCase()
  const rows = bookings.filter(
    (booking) =>
      (!status || booking.status === status) &&
      (!text || `${booking.eventTitle} ${booking.bookingId}`.toLowerCase().includes(text)),
  )
  const hasNext = bookings.length === PAGE_SIZE

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-4">
      <h1 className="text-2xl font-semibold tracking-tight">{t('myBookings.title')}</h1>
      <div className="flex flex-wrap gap-2">
        <Input
          type="search"
          className="max-w-xs"
          value={needle}
          onChange={(event) => setNeedle(event.target.value)}
          placeholder={t('myBookings.search')}
          aria-label={t('myBookings.search')}
        />
        <Select value={status || ALL} onValueChange={(value) => setStatus(value === ALL ? '' : value)}>
          <SelectTrigger className="w-48" aria-label={t('myBookings.statusFilter')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('myBookings.allStatuses')}</SelectItem>
            {STATUSES.map((value) => (
              <SelectItem key={value} value={value}>
                {t(`myBookings.status.${value}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {rows.length === 0 ? (
        <StatusPanel icon={<Receipt />} title={t('myBookings.empty')} />
      ) : (
        <div className="bg-card overflow-x-auto rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('myBookings.colId')}</TableHead>
                <TableHead>{t('myBookings.colEvent')}</TableHead>
                <TableHead>{t('myBookings.colStatus')}</TableHead>
                <TableHead>{t('myBookings.colCreated')}</TableHead>
                <TableHead className="text-right">{t('myBookings.colTotal')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((booking) => (
                <TableRow key={booking.bookingId}>
                  <TableCell className="font-mono text-xs whitespace-nowrap">{booking.bookingId.slice(-8).toUpperCase()}</TableCell>
                  <TableCell>
                    <div className="font-medium">{booking.eventTitle}</div>
                    <div className="text-muted-foreground text-xs whitespace-nowrap">
                      {formatDateChip(booking.startsAt)} · {formatTime(booking.startsAt)}
                    </div>
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    <div>{t(`myBookings.status.${booking.status}`, booking.status)}</div>
                    {booking.refundAmountMinor != null && (
                      <div className="text-muted-foreground text-xs">
                        {t('myBookings.refundAmount', { amount: formatMoney(booking.refundAmountMinor, booking.currency) })}
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="whitespace-nowrap">{formatDate(booking.createdAt)}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">{formatMoney(booking.totalMinor, booking.currency)}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {booking.status === 'PENDING_PAYMENT' && (
                      <Button size="sm" asChild>
                        <Link to="/checkout/$bookingId" params={{ bookingId: booking.bookingId }}>
                          {t('myBookings.continuePay')}
                        </Link>
                      </Button>
                    )}
                    {booking.status === 'CONFIRMED' && (
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="outline" asChild>
                          <Link to="/me/tickets">{t('myBookings.viewTickets')}</Link>
                        </Button>
                        {canRefund(booking) && (
                          <Button size="sm" variant="outline" onClick={() => setRefunding(booking)}>
                            {t('myBookings.requestRefund')}
                          </Button>
                        )}
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {refunding && (
        <RefundDialog booking={refunding} open onOpenChange={(open) => !open && setRefunding(null)} />
      )}

      <nav className="flex items-center justify-between text-sm" aria-label={t('myBookings.paging')}>
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
          {t('tickets.prev')}
        </Button>
        <span>{t('myBookings.page', { page: page + 1 })}</span>
        <Button variant="outline" size="sm" disabled={!hasNext} onClick={() => setPage(page + 1)}>
          {t('tickets.next')}
        </Button>
      </nav>
    </div>
  )
}
