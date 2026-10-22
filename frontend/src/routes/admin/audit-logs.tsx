import { createFileRoute } from '@tanstack/react-router'

import { AdminAuditLogsPage } from '@/features/admin-audit-logs/AdminAuditLogsPage'
import { parseAdminAuditLogSearch } from '@/features/admin-audit-logs/adminAuditLogSearch'

export const Route = createFileRoute('/admin/audit-logs')({
  validateSearch: parseAdminAuditLogSearch,
  component: AdminAuditLogsRoute,
})

function AdminAuditLogsRoute() {
  const search = Route.useSearch()
  return <AdminAuditLogsPage search={search} />
}
