import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { requireRole } from '@/features/auth/guards'

export const Route = createFileRoute('/organizer/')({
  beforeLoad: ({ context, location }) => requireRole(context.queryClient, 'ORGANIZER', location.href),
  component: OrganizerHomePage,
})

function OrganizerHomePage() {
  const { t } = useTranslation()
  return <h1 className="text-2xl font-semibold">{t('organizer.title')}</h1>
}
