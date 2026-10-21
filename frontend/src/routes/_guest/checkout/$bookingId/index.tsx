import { createFileRoute } from '@tanstack/react-router'

import { CheckoutPage } from '@/features/booking/CheckoutPage'

export const Route = createFileRoute('/_guest/checkout/$bookingId/')({ component: CheckoutRoute })

function CheckoutRoute() {
  const { bookingId } = Route.useParams()
  return <CheckoutPage bookingId={bookingId} />
}
