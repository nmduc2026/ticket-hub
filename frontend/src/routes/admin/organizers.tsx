import { createFileRoute } from '@tanstack/react-router'

import { AdminOrganizersPage } from '@/features/admin-organizers/AdminOrganizersPage'
import { parseAdminOrganizerSearch } from '@/features/admin-organizers/adminOrganizerSearch'

export const Route = createFileRoute('/admin/organizers')({
  validateSearch: parseAdminOrganizerSearch,
  component: AdminOrganizersRoute,
})

function AdminOrganizersRoute() {
  const search = Route.useSearch()
  return <AdminOrganizersPage search={search} />
}
