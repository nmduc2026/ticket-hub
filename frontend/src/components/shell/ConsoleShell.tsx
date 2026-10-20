import { Link, Outlet, useRouterState } from '@tanstack/react-router'
import { ChevronRight, Menu } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useCurrentUser } from '@/features/auth/useCurrentUser'
import { cn } from '@/lib/utils'

import { BrandMark } from './BrandMark'
import { CommandPalette } from './CommandPalette'
import { type ShellConfig, type ShellVariant, SHELL_CONFIG } from './nav'
import { NotificationBell } from './NotificationBell'
import { ThemeToggle } from './ThemeToggle'
import { UserMenu } from './UserMenu'

/**
 * Khung Admin / Organizer theo docs/design/README.md §3: sidebar nhóm (không số đếm, user ở cuối), topbar ghim có
 * breadcrumb luôn bắt đầu bằng "Trang chủ ›", Tìm nhanh (Ctrl K), đổi sáng / tối và chuông thông báo. Dưới 1024px
 * sidebar thành ngăn kéo mở bằng nút menu.
 */
export function ConsoleShell({ variant }: { variant: ShellVariant }) {
  const { t } = useTranslation()
  const config = SHELL_CONFIG[variant]
  const { user } = useCurrentUser()
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const [drawerOpen, setDrawerOpen] = useState(false)

  const activeItem = config.groups
    .flatMap((group) => group.items)
    .filter((item) => pathname === item.to || pathname.startsWith(`${item.to}/`))
    .sort((a, b) => b.to.length - a.to.length)[0]

  return (
    <div className="flex min-h-screen">
      <a
        href="#main"
        className="bg-background sr-only z-50 rounded-md px-3 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        {t('shell.skipToContent')}
      </a>
      <aside
        className="bg-sidebar text-sidebar-foreground border-sidebar-border sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r lg:flex"
        aria-label={t(config.navLabelKey)}
      >
        <SidebarContent config={config} pathname={pathname} />
        {user && (
          <div className="p-2">
            <UserMenu user={user} placement="sidebar" />
          </div>
        )}
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="bg-background sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 border-b px-4 lg:px-6">
          <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="lg:hidden" aria-label={t('common.openMenu')}>
                <Menu />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="bg-sidebar w-64 p-0">
              <SheetTitle className="sr-only">{t(config.navLabelKey)}</SheetTitle>
              <SheetDescription className="sr-only">{t(config.navLabelKey)}</SheetDescription>
              <SidebarContent config={config} pathname={pathname} onNavigate={() => setDrawerOpen(false)} />
              {user && (
                <div className="p-2">
                  <UserMenu user={user} placement="sidebar" />
                </div>
              )}
            </SheetContent>
          </Sheet>

          <nav aria-label={t('shell.breadcrumbLabel')} className="min-w-0">
            <ol className="text-muted-foreground flex items-center gap-1.5 text-sm">
              <li>
                <Link to={config.home} className="hover:text-foreground hover:underline">
                  {t('shell.home')}
                </Link>
              </li>
              <li aria-hidden="true" className="flex">
                <ChevronRight className="size-3.5" />
              </li>
              <li className="text-foreground truncate" aria-current="page">
                {t(activeItem?.labelKey ?? 'shell.overview')}
              </li>
            </ol>
          </nav>

          <span className="flex-1" />
          <CommandPalette groups={config.groups} />
          <ThemeToggle />
          <NotificationBell />
        </header>

        <main id="main" className="flex flex-1 flex-col gap-4 p-4 lg:px-7 lg:py-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function SidebarContent({
  config,
  pathname,
  onNavigate,
}: {
  config: ShellConfig
  pathname: string
  onNavigate?: () => void
}) {
  const { t } = useTranslation()

  return (
    <>
      <Link to={config.home} onClick={onNavigate} className="flex items-center gap-2.5 px-4 pt-4 pb-4">
        <BrandMark />
        <span className="flex flex-col leading-tight">
          <b className="text-sm font-bold">{t('app.name')}</b>
          <span className="text-muted-foreground text-xs">{t(config.subtitleKey)}</span>
        </span>
      </Link>
      <nav className="flex flex-1 flex-col gap-3.5 overflow-y-auto px-2" aria-label={t(config.navLabelKey)}>
        {config.groups.map((group) => (
          <div key={group.headingKey} className="flex flex-col gap-px">
            <div className="text-muted-foreground px-2.5 py-1 text-xs font-semibold">{t(group.headingKey)}</div>
            {group.items.map((item) => {
              const active = pathname === item.to || pathname.startsWith(`${item.to}/`)
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={onNavigate}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'flex min-h-9 items-center gap-2.5 rounded-lg px-2.5 text-sm font-medium',
                    'hover:bg-sidebar-accent/60',
                    active && 'bg-sidebar-accent text-sidebar-accent-foreground font-semibold',
                  )}
                >
                  <item.icon className={cn('size-[18px] shrink-0', !active && 'text-muted-foreground')} />
                  <span>{t(item.labelKey)}</span>
                </Link>
              )
            })}
          </div>
        ))}
      </nav>
    </>
  )
}
