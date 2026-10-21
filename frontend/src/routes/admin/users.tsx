import { createFileRoute } from '@tanstack/react-router'

import { AdminUsersPage } from '@/features/admin-users/AdminUsersPage'
import { parseAdminUserSearch } from '@/features/admin-users/adminUserSearch'

export const Route = createFileRoute('/admin/users')({
  validateSearch: parseAdminUserSearch,
  component: AdminUsersRoute,
})

function AdminUsersRoute() {
  const search = Route.useSearch()
  return <AdminUsersPage search={search} />
}
