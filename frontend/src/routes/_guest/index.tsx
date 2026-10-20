import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { useCurrentUser } from '@/features/auth/useCurrentUser'

export const Route = createFileRoute('/_guest/')({ component: HomePage })

function HomePage() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()

  return (
    <section className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-semibold">{t('home.title')}</h1>
      <p className="text-muted-foreground mt-2">
        {user ? t('home.greeting', { name: user.fullName ?? user.username }) : t('home.guest')}
      </p>
    </section>
  )
}
