import { Link, Outlet } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { useCurrentUser } from '@/features/auth/useCurrentUser'
import { redirectToLogin } from '@/lib/auth'

import { BrandMark } from './BrandMark'
import { NotificationBell } from './NotificationBell'
import { ThemeToggle } from './ThemeToggle'
import { UserMenu } from './UserMenu'

/**
 * Layout khách hàng (docs/design/README.md §3): header 64px (logo · sáng/tối · chuông · avatar hoặc Đăng nhập) và
 * footer xanh đêm. Ô tìm kiếm, link "Bán vé cùng Ticket Hub" / "Vé của tôi" và tab danh mục thêm khi các trang
 * đó có (Sprint 2 trở đi).
 */
export function GuestLayout() {
  const { t } = useTranslation()
  const { user, isLoading } = useCurrentUser()

  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="bg-background sr-only z-50 rounded-md px-3 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        {t('shell.skipToContent')}
      </a>
      <header className="bg-background sticky top-0 z-20 flex h-16 items-center gap-3 border-b px-4 md:px-6">
        <Link to="/" className="flex items-center gap-2.5">
          <BrandMark />
          <b className="text-base font-bold">{t('app.name')}</b>
        </Link>
        <span className="flex-1" />
        <ThemeToggle />
        <NotificationBell />
        {isLoading ? null : user ? (
          <UserMenu user={user} placement="avatar" />
        ) : (
          <Button size="sm" onClick={() => redirectToLogin()}>
            {t('auth.login')}
          </Button>
        )}
      </header>

      <main id="main" className="flex-1 px-4 py-6 md:px-6">
        <Outlet />
      </main>

      <footer className="bg-night text-night-foreground px-4 py-8 md:px-6">
        <div className="mx-auto flex max-w-6xl flex-col gap-2">
          <div className="flex items-center gap-2.5">
            <BrandMark />
            <b className="text-base font-bold">{t('app.name')}</b>
          </div>
          <p className="text-night-muted max-w-md text-sm">{t('guest.footerTagline')}</p>
          <p className="text-night-muted text-xs">{t('guest.footerCopyright')}</p>
        </div>
      </footer>
    </div>
  )
}
