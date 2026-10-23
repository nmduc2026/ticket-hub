import { createFileRoute } from '@tanstack/react-router'

import { requireUser } from '@/features/auth/guards'
import { SuccessPage } from '@/features/booking/SuccessPage'

export const Route = createFileRoute('/_guest/checkout/$bookingId/success')({
  beforeLoad: async ({ context, location }) => {
    await requireUser(context.queryClient, location.href)
  },
  component: SuccessRoute,
})

function SuccessRoute() {
  const { bookingId } = Route.useParams()
  return <SuccessPage bookingId={bookingId} />
}
