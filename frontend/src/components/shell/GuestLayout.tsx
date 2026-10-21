import { Link, Outlet, useNavigate, useRouterState } from '@tanstack/react-router'
import { Search } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useCurrentUser } from '@/features/auth/useCurrentUser'
import { EVENT_CATEGORIES } from '@/features/events/categories'
import { redirectToLogin } from '@/lib/auth'

import { BrandMark } from './BrandMark'
import { NotificationBell } from './NotificationBell'
import { ThemeToggle } from './ThemeToggle'
import { UserMenu } from './UserMenu'

/**
 * Layout khách hàng (docs/design/README.md §3, prototype #1): header 64px (logo · ô tìm kiếm · "Bán vé cùng Ticket
 * Hub" · "Vé của tôi" · sáng/tối · chuông · avatar hoặc Đăng nhập), tab danh mục ở trang chủ và footer xanh đêm. Màn hẹp
 * giữ logo, nút tìm kiếm và tài khoản; hai link kia có trong menu tài khoản.
 */
export function GuestLayout() {
  const { t } = useTranslation()
  const { user, isLoading } = useCurrentUser()
  const isHome = useRouterState({ select: (state) => state.location.pathname === '/' })

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
        <HeaderSearch />
        <span className="flex-1" />
        <nav aria-label={t('guest.headerNav')} className="hidden items-center gap-1 lg:flex">
          <Button variant="ghost" size="sm" asChild>
            <Link to="/organizer/apply">{t('guest.sellWithUs')}</Link>
          </Button>
          <Button variant="ghost" size="sm" asChild>
            <Link to="/me/tickets">{t('guest.myTickets')}</Link>
          </Button>
        </nav>
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
      {isHome && <CategoryTabs />}

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

/** Ô tìm kiếm: gửi sang danh sách sự kiện với `q`. Màn hẹp chỉ còn nút mở danh sách sự kiện. */
function HeaderSearch() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  return (
    <>
      <form
        role="search"
        className="relative hidden max-w-md flex-1 items-center md:flex"
        onSubmit={(submit) => {
          submit.preventDefault()
          const q = query.trim()
          void navigate({ to: '/events', search: q ? { q } : {} })
        }}
      >
        <Search className="text-muted-foreground pointer-events-none absolute left-3 size-4" aria-hidden="true" />
        <Input
          type="search"
          aria-label={t('guest.searchPlaceholder')}
          placeholder={t('guest.searchHint')}
          value={query}
          onChange={(change) => setQuery(change.target.value)}
          className="bg-muted/50 pl-9"
        />
      </form>
      <Button variant="ghost" size="icon" className="md:hidden" aria-label={t('guest.searchPlaceholder')} asChild>
        <Link to="/events">
          <Search />
        </Link>
      </Button>
    </>
  )
}

/** Tab danh mục dưới header trang chủ (prototype #1): mỗi tab mở danh sách sự kiện đã lọc theo danh mục. */
function CategoryTabs() {
  const { t } = useTranslation()
  return (
    <nav aria-label={t('guest.categoryNav')} className="bg-background border-b px-4 md:px-6">
      <ul className="mx-auto flex max-w-6xl gap-6 overflow-x-auto text-sm">
        <li>
          <Link to="/events" className="text-foreground block border-b-2 border-primary py-2.5 font-medium whitespace-nowrap">
            {t('guest.allCategories')}
          </Link>
        </li>
        {EVENT_CATEGORIES.map((category) => (
          <li key={category}>
            <Link
              to="/events"
              search={{ category }}
              className="text-muted-foreground hover:text-foreground block border-b-2 border-transparent py-2.5 whitespace-nowrap"
            >
              {category}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
