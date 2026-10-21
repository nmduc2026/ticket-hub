import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { AlertCircle, Clock, Ticket } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getGetEventSeatsQueryKey, useGetEvent } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import { useCancelBooking, useGetBooking } from '@/api/generated/endpoints/booking-controller/booking-controller'
import type { Booking } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api-client'
import { errorMessage } from '@/lib/error-messages'
import { formatEventRange } from '@/lib/date'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/utils'

import { formatCountdown, useCountdown } from './useCountdown'

const URGENT_SECONDS = 60

/**
 * Phần giữ ghế của màn thanh toán (#6): đồng hồ theo `expiresAt`, ghế đang giữ, tổng, "Huỷ và chọn lại ghế" (BR-12).
 * Phần thanh toán (Stripe, phí nền tảng) vào Sprint 3.
 */
export function CheckoutPage({ bookingId }: { bookingId: string }) {
  const { t } = useTranslation()
  const query = useGetBooking(bookingId, { query: { staleTime: 0, retry: false } })
  const booking = query.data?.status === 200 ? query.data.data : undefined

  if (query.isPending) return <Skeleton className="mx-auto h-80 max-w-2xl" aria-busy="true" />
  if (query.isError || !booking) {
    const notFound = query.error instanceof ApiError && query.error.status === 404
    return (
      <StatusPanel
        icon={<AlertCircle />}
        title={notFound ? t('checkout.notFoundTitle') : t('checkout.loadError')}
      >
        {!notFound && (
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        )}
        <Button variant="link" asChild>
          <Link to="/events">{t('eventDetail.backToList')}</Link>
        </Button>
      </StatusPanel>
    )
  }
  return <Hold booking={booking} refetch={() => void query.refetch()} />
}

function Hold({ booking, refetch }: { booking: Booking; refetch: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const event = useGetEvent(booking.eventId, { query: { staleTime: 60_000, retry: false } })
  const detail = event.data?.status === 200 ? event.data.data : undefined
  const remaining = useCountdown(booking.status === 'PENDING_PAYMENT' ? booking.expiresAt : undefined)
  const [cancelError, setCancelError] = useState<string | null>(null)
  const cancel = useCancelBooking()

  // Hết giờ ở client thì hỏi lại server để có trạng thái thật (job hết hạn chạy mỗi 30 giây).
  const expiredOnClient = remaining === 0
  useEffect(() => {
    if (expiredOnClient) refetch()
  }, [expiredOnClient, refetch])

  const backToSeats = () => {
    void queryClient.invalidateQueries({ queryKey: getGetEventSeatsQueryKey(booking.eventId) })
    void navigate({ to: '/events/$eventId/seats', params: { eventId: booking.eventId } })
  }

  if (booking.status === 'EXPIRED' || expiredOnClient) {
    return (
      <StatusPanel icon={<Clock />} title={t('checkout.expiredTitle')} body={t('checkout.expiredBody')}>
        <Button onClick={backToSeats}>{t('checkout.backToSeats')}</Button>
      </StatusPanel>
    )
  }
  if (booking.status === 'CANCELLED') {
    return (
      <StatusPanel icon={<Ticket />} title={t('checkout.cancelledTitle')}>
        <Button onClick={backToSeats}>{t('checkout.backToSeats')}</Button>
      </StatusPanel>
    )
  }
  if (booking.status !== 'PENDING_PAYMENT') {
    return (
      <StatusPanel icon={<Ticket />} title={t('checkout.closedTitle')}>
        <Button variant="link" asChild>
          <Link to="/events">{t('eventDetail.backToList')}</Link>
        </Button>
      </StatusPanel>
    )
  }

  const urgent = remaining !== null && remaining <= URGENT_SECONDS
  const onCancel = () => {
    setCancelError(null)
    cancel.mutate(
      { bookingId: booking.id },
      {
        onSuccess: () => {
          toast.success(t('checkout.cancelled'))
          backToSeats()
        },
        onError: (error) => {
          setCancelError(errorMessage(error, t))
          // Đơn đã hết hạn / không còn chờ thanh toán: tải lại để hiện đúng trạng thái.
          if (error instanceof ApiError && error.code === 'BOOKING_NOT_CANCELLABLE') refetch()
        },
      },
    )
  }

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      <header className="flex flex-col gap-1">
        <p className="text-muted-foreground text-sm">{t('checkout.step')}</p>
        <h1 className="text-2xl font-semibold tracking-tight">{t('checkout.title')}</h1>
        {detail && (
          <p className="text-muted-foreground text-sm">
            {detail.title} · {formatEventRange(detail.startsAt, detail.endsAt)}
          </p>
        )}
      </header>

      <div
        className={cn(
          'flex items-center justify-between rounded-xl border px-4 py-3',
          urgent && 'border-destructive/50 bg-destructive/5',
        )}
      >
        <span className="text-sm font-medium">{t('checkout.holdLeft')}</span>
        {/* Đọc thông báo theo phút, không đọc từng giây. */}
        <span
          className={cn('font-mono text-2xl font-semibold tabular-nums', urgent && 'text-destructive')}
          role="timer"
          aria-label={t('checkout.holdLeft')}
        >
          {remaining === null ? '--:--' : formatCountdown(remaining)}
        </span>
      </div>

      <section aria-label={t('checkout.seats')} className="bg-card rounded-xl border">
        <ul className="divide-y">
          {booking.items.map((item) => (
            <li key={item.seatId} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
              <span>
                {t('checkout.seatLine', { tier: item.tierName, row: item.row, number: item.number })}
              </span>
              <span className="whitespace-nowrap">{formatMoney(item.priceMinor, booking.currency)}</span>
            </li>
          ))}
        </ul>
        <div className="flex items-baseline justify-between border-t px-4 py-3">
          <span className="text-muted-foreground text-sm">{t('checkout.tickets', { count: booking.items.length })}</span>
          <strong className="text-lg whitespace-nowrap">{formatMoney(booking.totalMinor, booking.currency)}</strong>
        </div>
      </section>

      {cancelError && (
        <div role="alert" className="border-destructive/40 bg-destructive/5 rounded-lg border px-4 py-2.5 text-sm">
          {cancelError}
        </div>
      )}
      <div className="flex justify-end">
        <Button variant="outline" disabled={cancel.isPending} onClick={onCancel}>
          {t('checkout.cancel')}
        </Button>
      </div>
    </div>
  )
}
