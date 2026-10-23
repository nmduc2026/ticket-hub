import { createFileRoute } from '@tanstack/react-router'

import { OrganizerEventsPage } from '@/features/organizer-events/OrganizerEventsPage'
import { parseOrganizerEventSearch } from '@/features/organizer-events/organizerEventSearch'

export const Route = createFileRoute('/organizer/events/')({
  validateSearch: parseOrganizerEventSearch,
  component: OrganizerEventsRoute,
})

function OrganizerEventsRoute() {
  const search = Route.useSearch()
  return <OrganizerEventsPage search={search} />
}
