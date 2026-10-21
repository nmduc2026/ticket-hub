import { createFileRoute } from '@tanstack/react-router'

import { requireUser } from '@/features/auth/guards'
import { TicketDetailPage } from '@/features/tickets/TicketDetailPage'

export const Route = createFileRoute('/_guest/me/tickets/$ticketId')({
  beforeLoad: async ({ context, location }) => {
    await requireUser(context.queryClient, location.href)
  },
  component: TicketDetailRoute,
})

function TicketDetailRoute() {
  const { ticketId } = Route.useParams()
  return <TicketDetailPage ticketId={ticketId} />
}
