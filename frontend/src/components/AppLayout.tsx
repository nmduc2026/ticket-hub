import { Link, Outlet } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { hasRole } from '@/features/auth/roles'
import { useCurrentUser } from '@/features/auth/useCurrentUser'
import { logout, redirectToLogin } from '@/lib/auth'

/** Khung tối giản cho Sprint 1 (việc 7); khung đầy đủ theo prototype ở việc 7b. */
export function AppLayout() {
  const { t } = useTranslation()
  const { user, isLoading } = useCurrentUser()

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex h-16 items-center gap-6 border-b px-4">
        <Link to="/" className="text-lg font-bold">
          {t('app.name')}
        </Link>
        <nav className="flex flex-1 items-center gap-4 text-sm">
          {user && hasRole(user.roles, 'ORGANIZER') && <Link to="/organizer">{t('nav.organizer')}</Link>}
          {user && hasRole(user.roles, 'ADMIN') && <Link to="/admin">{t('nav.admin')}</Link>}
        </nav>
        {isLoading ? null : user ? (
          <div className="flex items-center gap-3 text-sm">
            <span>{user.fullName ?? user.username}</span>
            <Button variant="outline" size="sm" onClick={() => void logout()}>
              {t('auth.logout')}
            </Button>
          </div>
        ) : (
          <Button size="sm" onClick={() => redirectToLogin()}>
            {t('auth.login')}
          </Button>
        )}
      </header>
      <main className="flex-1 p-4">
        <Outlet />
      </main>
    </div>
  )
}
