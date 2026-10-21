import type { Ticket } from '@/api/generated/model'

export type TicketTab = 'upcoming' | 'past' | 'refunded'

/** Tab của vé (docs/design/checkout.md §4): hoàn = bị thu hồi; đã qua = đã dùng hoặc sự kiện đã bắt đầu; còn lại là sắp diễn ra. */
export function ticketTab(ticket: Ticket, now: number = Date.now()): TicketTab {
  if (ticket.status === 'REVOKED') return 'refunded'
  if (ticket.status === 'USED' || new Date(ticket.startsAt).getTime() < now) return 'past'
  return 'upcoming'
}
