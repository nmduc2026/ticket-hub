import { createFileRoute } from '@tanstack/react-router'

import { SeatSelectPage } from '@/features/booking/SeatSelectPage'

export const Route = createFileRoute('/_guest/events/$eventId/seats')({ component: SeatSelectRoute })

function SeatSelectRoute() {
  const { eventId } = Route.useParams()
  return <SeatSelectPage eventId={eventId} />
}
