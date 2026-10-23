import { createFileRoute } from '@tanstack/react-router'

import { EventDashboardTab } from '@/features/organizer-events/EventDashboardTab'

export const Route = createFileRoute('/organizer/events/$eventId/dashboard')({ component: DashboardRoute })

function DashboardRoute() {
  const { eventId } = Route.useParams()
  return <EventDashboardTab eventId={eventId} />
}
