import { createFileRoute } from '@tanstack/react-router'

import { EventListPage } from '@/features/events/EventListPage'
import { parseEventSearch } from '@/features/events/eventSearchParams'

export const Route = createFileRoute('/_guest/events/')({
  validateSearch: parseEventSearch,
  component: EventListRoute,
})

function EventListRoute() {
  const search = Route.useSearch()
  return <EventListPage search={search} />
}
