import { createFileRoute } from '@tanstack/react-router'

import { TiersTab } from '@/features/organizer-events/TiersTab'

export const Route = createFileRoute('/organizer/events/$eventId/tiers')({ component: TiersRoute })

function TiersRoute() {
  const { eventId } = Route.useParams()
  return <TiersTab eventId={eventId} />
}
