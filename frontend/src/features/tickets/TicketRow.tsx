import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import type { Ticket } from '@/api/generated/model'
import { formatDateChip, formatTime } from '@/lib/date'

/** Một dòng trong "Vé của tôi" (#8): sự kiện, giờ, địa điểm, vị trí, trạng thái; bấm mở chi tiết vé (#9). */
export function TicketRow({ ticket }: { ticket: Ticket }) {
  const { t } = useTranslation()
  return (
    <li>
      <Link
        to="/me/tickets/$ticketId"
        params={{ ticketId: ticket.ticketId }}
        className="hover:bg-accent focus-visible:ring-ring flex items-center justify-between gap-3 rounded-xl border p-4 outline-none focus-visible:ring-2"
      >
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="truncate font-semibold">{ticket.eventTitle}</span>
          <span className="text-muted-foreground truncate text-sm">
            {formatDateChip(ticket.startsAt)} · {formatTime(ticket.startsAt)} · {ticket.venueName}
          </span>
          <span className="text-sm">
            {ticket.tierName} · {t('tickets.seat', { row: ticket.seat.row, number: ticket.seat.number })}
          </span>
        </span>
        <span className="text-muted-foreground shrink-0 text-sm">{t(`tickets.status.${ticket.status}`)}</span>
      </Link>
    </li>
  )
}
