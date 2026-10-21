import { createFileRoute } from '@tanstack/react-router'

import { requireUser } from '@/features/auth/guards'
import { MyBookingsPage } from '@/features/my-bookings/MyBookingsPage'

export const Route = createFileRoute('/_guest/me/bookings')({
  beforeLoad: async ({ context, location }) => {
    await requireUser(context.queryClient, location.href)
  },
  component: MyBookingsPage,
})
