import { createFileRoute } from '@tanstack/react-router'

import { requireUser } from '@/features/auth/guards'
import { NotificationsPage } from '@/features/notifications/NotificationsPage'

export const Route = createFileRoute('/_guest/me/notifications')({
  beforeLoad: async ({ context, location }) => {
    await requireUser(context.queryClient, location.href)
  },
  component: NotificationsPage,
})
