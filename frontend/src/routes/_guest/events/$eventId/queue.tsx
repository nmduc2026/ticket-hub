import { createFileRoute } from '@tanstack/react-router'

import { QueuePage } from '@/features/queue/QueuePage'

export const Route = createFileRoute('/_guest/events/$eventId/queue')({ component: QueueRoute })

function QueueRoute() {
  const { eventId } = Route.useParams()
  return <QueuePage eventId={eventId} />
}
