import { Link } from '@tanstack/react-router'
import { AlertCircle, CalendarPlus } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'
import { useTranslation } from 'react-i18next'

import { useGetEvent } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import type { Ticket } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateChip, formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'

import { buildIcs, downloadIcs } from './calendar'
import { useMyTickets } from './useMyTickets'

/** Chi tiết vé (#9): mã QR từ `qrToken`, vé không còn hợp lệ thì QR mờ kèm nhãn trạng thái (docs/design/checkout.md §4). */
export function TicketDetailPage({ ticketId }: { ticketId: string }) {
  const { t } = useTranslation()
  const { tickets, isPending, isError, refetch } = useMyTickets()
  const ticket = tickets.find((item) => item.ticketId === ticketId)

  if (isPending) return <Skeleton className="mx-auto h-96 max-w-sm" aria-busy="true" />
  if (isError || !ticket) {
    return (
      <StatusPanel icon={<AlertCircle />} title={isError ? t('tickets.loadError') : t('tickets.notFound')}>
        {isError && (
          <Button variant="outline" size="sm" onClick={() => void refetch()}>
            {t('common.retry')}
          </Button>
        )}
        <Button variant="link" asChild>
          <Link to="/me/tickets">{t('tickets.back')}</Link>
        </Button>
      </StatusPanel>
    )
  }

  const valid = ticket.status === 'VALID'
  return (
    <div className="mx-auto flex max-w-sm flex-col items-center gap-4">
      <Button variant="link" asChild className="self-start px-0">
        <Link to="/me/tickets">{t('tickets.back')}</Link>
      </Button>
      <section className="bg-card flex w-full flex-col items-center gap-3 rounded-xl border p-6 text-center">
        <p className="text-muted-foreground text-sm">
          {formatDateChip(ticket.startsAt)} · {formatTime(ticket.startsAt)}
          {ticket.doorsOpenAt && ` (${t('tickets.doorsOpen', { time: formatTime(ticket.doorsOpenAt) })})`}
        </p>
        <h1 className="text-xl font-semibold">{ticket.eventTitle}</h1>
        <p className="text-muted-foreground text-sm">{ticket.venueName}</p>
        <div className={cn('rounded-lg bg-white p-2', !valid && 'opacity-30')}>
          <QRCodeSVG value={ticket.qrToken} size={208} level="M" role="img" aria-label={t('tickets.qr')} />
        </div>
        <p className="font-medium">
          {ticket.tierName} · {t('tickets.seat', { row: ticket.seat.row, number: ticket.seat.number })}
        </p>
        <p className={cn('text-sm', valid ? 'text-primary font-medium' : 'text-muted-foreground')}>
          {t(`tickets.status.${ticket.status}`)}
        </p>
        {valid && <AddToCalendar ticket={ticket} />}
        {ticket.ageRestriction != null && (
          <p className="text-muted-foreground text-sm">{t('tickets.ageRestriction', { age: ticket.ageRestriction })}</p>
        )}
      </section>
      {ticket.rules && (
        <section aria-labelledby="ticket-rules" className="bg-card flex w-full flex-col gap-1 rounded-xl border p-4">
          <h2 id="ticket-rules" className="text-sm font-semibold">
            {t('tickets.rules')}
          </h2>
          <p className="text-muted-foreground text-sm whitespace-pre-line">{ticket.rules}</p>
        </section>
      )}
    </div>
  )
}

/** "Thêm vào lịch": file `.ics` sinh ở trình duyệt; giờ kết thúc lấy từ trang sự kiện (có cache) nếu có. */
function AddToCalendar({ ticket }: { ticket: Ticket }) {
  const { t } = useTranslation()
  const event = useGetEvent(ticket.eventId, { query: { staleTime: 5 * 60_000, retry: false } })
  const endsAt = event.data?.status === 200 ? event.data.data.endsAt : undefined
  const add = () =>
    downloadIcs(
      `ticket-hub-${ticket.ticketId.slice(-8)}.ics`,
      buildIcs({
        uid: ticket.ticketId,
        title: ticket.eventTitle,
        location: ticket.venueName,
        startsAt: ticket.startsAt,
        endsAt,
        description: `${ticket.tierName} · ${t('tickets.seat', { row: ticket.seat.row, number: ticket.seat.number })}`,
      }),
    )
  return (
    <Button variant="outline" size="sm" onClick={add}>
      <CalendarPlus aria-hidden="true" />
      {t('tickets.addToCalendar')}
    </Button>
  )
}
