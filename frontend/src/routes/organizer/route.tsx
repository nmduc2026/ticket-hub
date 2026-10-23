import { createFileRoute } from '@tanstack/react-router'

import { ConsoleShell } from '@/components/shell/ConsoleShell'
import { requireRole } from '@/features/auth/guards'

/** Mọi route con của /organizer cần role ORGANIZER; khung Organizer dùng chung cho cả nhóm. */
export const Route = createFileRoute('/organizer')({
  beforeLoad: ({ context, location }) => requireRole(context.queryClient, 'ORGANIZER', location.href),
  component: () => <ConsoleShell variant="organizer" />,
})
