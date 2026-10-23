import { createFileRoute } from '@tanstack/react-router'

import { OrganizerOrdersPage } from '@/features/organizer-orders/OrganizerOrdersPage'
import { parseOrganizerOrderSearch } from '@/features/organizer-orders/organizerOrderSearch'

export const Route = createFileRoute('/organizer/orders')({
  validateSearch: parseOrganizerOrderSearch,
  component: OrganizerOrdersRoute,
})

function OrganizerOrdersRoute() {
  const search = Route.useSearch()
  return <OrganizerOrdersPage search={search} />
}
