import { createFileRoute } from '@tanstack/react-router'

import { SeatMapEditorPage } from '@/features/admin-seat-map/SeatMapEditorPage'

export const Route = createFileRoute('/admin/venues_/$venueId/seat-maps/$mapId')({ component: EditorRoute })

function EditorRoute() {
  const { venueId, mapId } = Route.useParams()
  return <SeatMapEditorPage venueId={venueId} mapId={mapId} />
}
