import { createFileRoute } from '@tanstack/react-router'

import { requireUser } from '@/features/auth/guards'
import { ProfilePage } from '@/features/profile/ProfilePage'

export const Route = createFileRoute('/_guest/me/profile')({
  beforeLoad: async ({ context, location }) => ({ user: await requireUser(context.queryClient, location.href) }),
  component: ProfileRoute,
})

function ProfileRoute() {
  const { user } = Route.useRouteContext()
  return <ProfilePage user={user} />
}
