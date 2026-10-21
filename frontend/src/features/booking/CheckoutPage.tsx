import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { AlertCircle, Clock, Ticket } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getGetEventSeatsQueryKey, useGetEvent } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import { useCancelBooking, useGetBooking } from '@/api/generated/endpoints/booking-controller/booking-controller'
import type { Booking } from '@/api/generated/model'
import { useCurrentUser } from '@/features/auth/useCurrentUser'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api-client'
import { MIN_PAYABLE_SECONDS } from '@/lib/config'
import { errorMessage } from '@/lib/error-messages'
import { formatEventRange } from '@/lib/date'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/utils'

import { Confirming } from './Confirming'
import { PaymentSection } from './PaymentSection'
import { formatCountdown, useCountdown } from './useCountdown'
import { useStartPayment } from './useStartPayment'

const URGENT_SECONDS = 60
const FAST_POLL_MS = 2_000
const SLOW_POLL_MS = 5_000
const FAST_POLL_WINDOW_MS = 30_000
const GIVE_UP_POLL_MS = 5 * 60_000

/**
 * Màn thanh toán (#6): đồng hồ theo `expiresAt`, ghế đang giữ, Stripe Payment Element, "Huỷ và chọn lại ghế" (BR-12).
 * Sau khi Stripe nhận tiền thì hỏi lại booking tới khi `CONFIRMED` (docs/design/checkout.md §2).
 */
export function CheckoutPage({ bookingId }: { bookingId: string }) {
  const { t } = useTranslation()
  const paidAt = useRef<number | null>(null)
  const [confirming, setConfirming] = useState(false)
  const query = useGetBooking(bookingId, {
    query: {
      staleTime: 0,
      retry: false,
      refetchInterval: () => {
        if (!confirming || paidAt.current === null) return false
        const waited = Date.now() - paidAt.current
        if (waited > GIVE_UP_POLL_MS) return false
        return waited < FAST_POLL_WINDOW_MS ? FAST_POLL_MS : SLOW_POLL_MS
      },
    },
  })
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
  return (
    <Hold
      booking={booking}
      refetch={() => void query.refetch()}
      confirming={confirming}
      onPaid={() => {
        paidAt.current = Date.now()
        setConfirming(true)
      }}
    />
  )
}

interface HoldProps {
  booking: Booking
  refetch: () => void
  confirming: boolean
  onPaid: () => void
}

function Hold({ booking, refetch, confirming, onPaid }: HoldProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const event = useGetEvent(booking.eventId, { query: { staleTime: 60_000, retry: false } })
  const detail = event.data?.status === 200 ? event.data.data : undefined
  const remaining = useCountdown(booking.status === 'PENDING_PAYMENT' ? booking.expiresAt : undefined)
  const [cancelError, setCancelError] = useState<string | null>(null)
  const cancel = useCancelBooking()
  const { user } = useCurrentUser()
  const lockedByTime = remaining !== null && remaining < MIN_PAYABLE_SECONDS
  const startPayment = useStartPayment(booking.id, booking.status === 'PENDING_PAYMENT' && !lockedByTime && !confirming)
  const holdExpiring = startPayment.error instanceof ApiError && startPayment.error.code === 'HOLD_EXPIRING'
  const notPayable = startPayment.error instanceof ApiError && startPayment.error.code === 'BOOKING_NOT_PAYABLE'

  // Hết giờ ở client thì hỏi lại server để có trạng thái thật (job hết hạn chạy mỗi 30 giây).
  const expiredOnClient = remaining === 0
  useEffect(() => {
    if (expiredOnClient) refetch()
  }, [expiredOnClient, refetch])

  const confirmed = booking.status === 'CONFIRMED'
  useEffect(() => {
    if (confirmed) void navigate({ to: '/checkout/$bookingId/success', params: { bookingId: booking.id }, replace: true })
  }, [confirmed, booking.id, navigate])
  useEffect(() => {
    if (notPayable) refetch()
  }, [notPayable, refetch])

  const backToSeats = () => {
    void queryClient.invalidateQueries({ queryKey: getGetEventSeatsQueryKey(booking.eventId) })
    void navigate({ to: '/events/$eventId/seats', params: { eventId: booking.eventId } })
  }

  if (confirming || confirmed) return <Confirming />
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
        <div className="flex flex-col gap-1 border-t px-4 py-3">
          <div className="flex items-baseline justify-between">
            <span className="text-muted-foreground text-sm">{t('checkout.tickets', { count: booking.items.length })}</span>
            <strong className="text-lg whitespace-nowrap">{formatMoney(booking.totalMinor, booking.currency)}</strong>
          </div>
          {startPayment.payment && (
            <p className="text-muted-foreground text-xs">
              {t('checkout.platformFee', { fee: formatMoney(startPayment.payment.platformFeeMinor, booking.currency) })}
            </p>
          )}
        </div>
      </section>

      {user && (
        <section aria-label={t('checkout.contactTitle')} className="bg-card rounded-xl border px-4 py-3 text-sm">
          <h2 className="mb-1 font-semibold">{t('checkout.contactTitle')}</h2>
          <p className="text-muted-foreground">
            {t('checkout.contactEmail')}: <span className="text-foreground">{user.email}</span>
          </p>
        </section>
      )}

      <section aria-label={t('checkout.paymentTitle')} className="bg-card flex flex-col gap-3 rounded-xl border p-4">
        <h2 className="font-semibold">{t('checkout.paymentTitle')}</h2>
        {lockedByTime || holdExpiring ? (
          <p role="alert" className="border-destructive/40 bg-destructive/5 rounded-lg border px-4 py-2.5 text-sm">
            {t('checkout.holdExpiring')}
          </p>
        ) : startPayment.payment ? (
          <PaymentSection
            payment={startPayment.payment}
            locked={lockedByTime}
            returnUrl={`${window.location.origin}/checkout/${booking.id}/success`}
            onPaid={onPaid}
          />
        ) : startPayment.error && !notPayable ? (
          <div role="alert" className="flex items-center justify-between gap-3 text-sm">
            <span>{errorMessage(startPayment.error, t)}</span>
            <Button variant="outline" size="sm" onClick={() => startPayment.retry()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : (
          <Skeleton className="h-40" aria-busy="true" aria-label={t('checkout.paymentLoading')} />
        )}
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
