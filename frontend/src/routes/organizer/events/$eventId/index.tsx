import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/organizer/events/$eventId/')({
  beforeLoad: ({ params }) => {
    throw redirect({ to: '/organizer/events/$eventId/edit', params, replace: true })
  },
})
