import { createFileRoute } from '@tanstack/react-router'

import { AdminEventsPage } from '@/features/admin-events/AdminEventsPage'
import { parseAdminEventSearch } from '@/features/admin-events/adminEventSearch'

export const Route = createFileRoute('/admin/events')({
  validateSearch: parseAdminEventSearch,
  component: AdminEventsRoute,
})

function AdminEventsRoute() {
  const search = Route.useSearch()
  return <AdminEventsPage search={search} />
}
