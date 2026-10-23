import { createFileRoute } from '@tanstack/react-router'

import { SeatMapListPage } from '@/features/admin-seat-map/SeatMapListPage'

export const Route = createFileRoute('/admin/venues_/$venueId/seat-maps/')({ component: ListRoute })

function ListRoute() {
  const { venueId } = Route.useParams()
  return <SeatMapListPage venueId={venueId} />
}
