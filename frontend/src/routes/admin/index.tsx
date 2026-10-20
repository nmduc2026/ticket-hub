import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export const Route = createFileRoute('/admin/')({ component: AdminHomePage })

function AdminHomePage() {
  const { t } = useTranslation()
  return <h1 className="text-2xl font-semibold tracking-tight">{t('admin.title')}</h1>
}
