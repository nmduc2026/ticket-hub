import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export const Route = createFileRoute('/organizer/')({ component: OrganizerHomePage })

function OrganizerHomePage() {
  const { t } = useTranslation()
  return <h1 className="text-2xl font-semibold tracking-tight">{t('organizer.title')}</h1>
}
