import { createFileRoute, Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export const Route = createFileRoute('/_guest/forbidden')({ component: ForbiddenPage })

function ForbiddenPage() {
  const { t } = useTranslation()

  return (
    <section className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-semibold">{t('forbidden.title')}</h1>
      <p className="text-muted-foreground mt-2">{t('forbidden.description')}</p>
      <Link to="/" className="text-primary mt-4 inline-block underline">
        {t('forbidden.back')}
      </Link>
    </section>
  )
}
