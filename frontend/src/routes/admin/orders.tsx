import { createFileRoute } from '@tanstack/react-router'

import { AdminOrdersPage } from '@/features/admin-orders/AdminOrdersPage'
import { parseAdminOrderSearch } from '@/features/admin-orders/adminOrderSearch'

export const Route = createFileRoute('/admin/orders')({
  validateSearch: parseAdminOrderSearch,
  component: AdminOrdersRoute,
})

function AdminOrdersRoute() {
  const search = Route.useSearch()
  return <AdminOrdersPage search={search} />
}
