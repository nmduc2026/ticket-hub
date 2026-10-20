import { createFileRoute } from '@tanstack/react-router'

import { ConsoleShell } from '@/components/shell/ConsoleShell'
import { requireRole } from '@/features/auth/guards'

/** Mọi route con của /admin cần role ADMIN; khung Admin dùng chung cho cả nhóm. */
export const Route = createFileRoute('/admin')({
  beforeLoad: ({ context, location }) => requireRole(context.queryClient, 'ADMIN', location.href),
  component: () => <ConsoleShell variant="admin" />,
})
