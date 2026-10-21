import { createFileRoute } from '@tanstack/react-router'

import { requireUser } from '@/features/auth/guards'
import { MyTicketsPage } from '@/features/tickets/MyTicketsPage'

export const Route = createFileRoute('/_guest/me/tickets/')({
  beforeLoad: async ({ context, location }) => {
    await requireUser(context.queryClient, location.href)
  },
  component: MyTicketsPage,
})
