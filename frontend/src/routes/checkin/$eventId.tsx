import { createFileRoute } from '@tanstack/react-router'

import { requireRole } from '@/features/auth/guards'
import { CheckInPage } from '@/features/checkin/CheckInPage'

/** Màn check-in toàn màn hình cho organizer, không dùng khung console (docs/design/checkin.md). */
export const Route = createFileRoute('/checkin/$eventId')({
  beforeLoad: ({ context, location }) => requireRole(context.queryClient, 'ORGANIZER', location.href),
  component: CheckInRoute,
})

function CheckInRoute() {
  const { eventId } = Route.useParams()
  return <CheckInPage eventId={eventId} />
}
