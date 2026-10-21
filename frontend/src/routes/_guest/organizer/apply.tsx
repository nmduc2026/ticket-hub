import { createFileRoute } from '@tanstack/react-router'

import { requireUser } from '@/features/auth/guards'
import { OrganizerApplyPage } from '@/features/organizer-apply/OrganizerApplyPage'

export const Route = createFileRoute('/_guest/organizer/apply')({
  beforeLoad: async ({ context, location }) => ({ user: await requireUser(context.queryClient, location.href) }),
  component: ApplyRoute,
})

function ApplyRoute() {
  const { user } = Route.useRouteContext()
  return <OrganizerApplyPage user={user} />
}
