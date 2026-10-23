import { createFileRoute } from '@tanstack/react-router'

import { EventEditLayout } from '@/features/organizer-events/EventEditLayout'

/** Khung sửa sự kiện (màn #13, #24): đầu trang, điều kiện publish và thanh tab dùng chung cho các tab con. */
export const Route = createFileRoute('/organizer/events/$eventId')({
  component: EventEditRoute,
})

function EventEditRoute() {
  const { eventId } = Route.useParams()
  return <EventEditLayout eventId={eventId} />
}
