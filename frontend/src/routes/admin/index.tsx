import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { requireRole } from '@/features/auth/guards'

export const Route = createFileRoute('/admin/')({
  beforeLoad: ({ context, location }) => requireRole(context.queryClient, 'ADMIN', location.href),
  component: AdminHomePage,
})

function AdminHomePage() {
  const { t } = useTranslation()
  return <h1 className="text-2xl font-semibold">{t('admin.title')}</h1>
}
