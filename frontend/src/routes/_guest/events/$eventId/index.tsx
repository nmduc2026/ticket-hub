import { createFileRoute } from '@tanstack/react-router'

import { EventDetailPage } from '@/features/events/EventDetailPage'

export const Route = createFileRoute('/_guest/events/$eventId/')({ component: EventDetailRoute })

function EventDetailRoute() {
  const { eventId } = Route.useParams()
  return <EventDetailPage eventId={eventId} />
}
