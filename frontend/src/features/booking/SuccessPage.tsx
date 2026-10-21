import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { AlertCircle, CheckCircle2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useGetBooking } from '@/api/generated/endpoints/booking-controller/booking-controller'
import { getListMyTicketsQueryKey, listMyTickets } from '@/api/generated/endpoints/my-ticket-controller/my-ticket-controller'
import { useGetEvent } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import type { Booking, Ticket } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useCurrentUser } from '@/features/auth/useCurrentUser'
import { formatEventRange } from '@/lib/date'
import { formatMoney } from '@/lib/money'

const TICKET_POLL_MS = 2_000
const TICKETS_PAGE = { page: 0, size: 100 }

/** Thanh toán thành công (#7). Đơn chưa `CONFIRMED` thì về màn thanh toán; vé phát chậm hơn xác nhận vài giây nên hỏi lại tới khi đủ. */
export function SuccessPage({ bookingId }: { bookingId: string }) {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const bookingQuery = useGetBooking(bookingId, { query: { staleTime: 0, retry: false } })
  const booking = bookingQuery.data?.status === 200 ? bookingQuery.data.data : undefined
  const eventQuery = useGetEvent(booking?.eventId ?? '', { query: { enabled: !!booking, staleTime: 60_000, retry: false } })
  const event = eventQuery.data?.status === 200 ? eventQuery.data.data : undefined

  const ticketsQuery = useQuery({
    queryKey: getListMyTicketsQueryKey(TICKETS_PAGE),
    queryFn: () => listMyTickets(TICKETS_PAGE),
    enabled: !!booking && !!event,
    refetchInterval: (query) => {
      const tickets = query.state.data?.status === 200 ? query.state.data.data : []
      const complete = !!booking && !!event && ticketsOf(tickets, booking, event.title).length >= booking.items.length
      return complete ? false : TICKET_POLL_MS
    },
  })

  if (bookingQuery.isPending) return <Skeleton className="mx-auto h-80 max-w-2xl" aria-busy="true" />
  if (bookingQuery.isError || !booking) {
    return (
      <StatusPanel icon={<AlertCircle />} title={t('checkout.loadError')}>
        <Button variant="outline" size="sm" onClick={() => void bookingQuery.refetch()}>
          {t('common.retry')}
        </Button>
      </StatusPanel>
    )
  }
  if (booking.status !== 'CONFIRMED') {
    return (
      <StatusPanel icon={<AlertCircle />} title={t('checkout.closedTitle')}>
        <Button asChild>
          <Link to="/checkout/$bookingId" params={{ bookingId }}>
            {t('checkout.title')}
          </Link>
        </Button>
      </StatusPanel>
    )
  }

  const allTickets = ticketsQuery.data?.status === 200 ? ticketsQuery.data.data : []
  const tickets = event ? ticketsOf(allTickets, booking, event.title) : []

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      <header className="flex flex-col items-center gap-2 text-center">
        <CheckCircle2 className="text-primary size-12" aria-hidden="true" />
        <h1 className="text-2xl font-semibold tracking-tight">{t('success.title')}</h1>
        <p className="text-muted-foreground text-sm">
          {user ? t('success.body', { email: user.email }) : t('success.bodyNoEmail')}
        </p>
      </header>

      <section aria-label={t('success.details')} className="bg-card rounded-xl border">
        <h2 className="border-b px-4 py-3 font-semibold">{t('success.details')}</h2>
        <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-2 px-4 py-3 text-sm">
          {event && (
            <>
              <dt className="text-muted-foreground">{t('success.event')}</dt>
              <dd className="font-medium">{event.title}</dd>
              <dt className="text-muted-foreground">{t('success.time')}</dt>
              <dd className="font-medium">{formatEventRange(event.startsAt, event.endsAt)}</dd>
              <dt className="text-muted-foreground">{t('success.venue')}</dt>
              <dd className="font-medium">{event.venueName}</dd>
            </>
          )}
          <dt className="text-muted-foreground">{t('success.paid')}</dt>
          <dd className="font-medium whitespace-nowrap">{formatMoney(booking.totalMinor, booking.currency)}</dd>
        </dl>
      </section>

      <section aria-label={t('success.tickets', { count: booking.items.length })} className="bg-card rounded-xl border">
        <h2 className="border-b px-4 py-3 font-semibold">{t('success.tickets', { count: booking.items.length })}</h2>
        {tickets.length === 0 ? (
          <Skeleton className="m-4 h-12" aria-busy="true" aria-label={t('success.ticketsLoading')} />
        ) : (
          <ul className="divide-y">
            {tickets.map((ticket) => (
              <li key={ticket.ticketId} className="px-4 py-3 text-sm">
                {ticket.tierName} · {t('checkout.seatLine', { tier: ticket.seat.sectionCode, row: ticket.seat.row, number: ticket.seat.number })}
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="flex justify-center gap-2">
        <Button asChild>
          <Link to="/me/tickets">{t('success.myTickets')}</Link>
        </Button>
        <Button variant="outline" asChild>
          <Link to="/">{t('success.home')}</Link>
        </Button>
      </div>
    </div>
  )
}

/** Vé của đơn này: cùng sự kiện và đúng các ghế của đơn (vé chưa mang mã đơn). */
function ticketsOf(tickets: Ticket[], booking: Booking, eventTitle: string): Ticket[] {
  const seats = new Set(booking.items.map((item) => `${item.sectionCode}|${item.row}|${item.number}`))
  return tickets.filter(
    (ticket) => ticket.eventTitle === eventTitle && seats.has(`${ticket.seat.sectionCode}|${ticket.seat.row}|${ticket.seat.number}`),
  )
}
