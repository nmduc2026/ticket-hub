import { Link } from '@tanstack/react-router'
import { ChevronsUpDown, Globe, LogOut, User } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { MeResponse } from '@/api/generated/model'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { hasRole } from '@/features/auth/roles'
import { logout } from '@/lib/auth'
import { initials } from '@/lib/initials'

interface UserMenuProps {
  user: MeResponse
  /** `sidebar`: nút rộng có tên + email ở cuối sidebar; `avatar`: chỉ avatar ở header khách. */
  placement: 'sidebar' | 'avatar'
}

/** Menu tài khoản dùng chung cho khung Admin / Organizer và header khách. */
export function UserMenu({ user, placement }: UserMenuProps) {
  const { t } = useTranslation()
  const isSidebar = placement === 'sidebar'
  const displayName = user.fullName ?? user.username ?? ''
  const avatar = (
    <Avatar>
      <AvatarFallback className="bg-primary/10 text-primary font-semibold">
        {initials(user.fullName, user.username)}
      </AvatarFallback>
    </Avatar>
  )

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        {isSidebar ? (
          <Button variant="ghost" className="h-12 w-full justify-start gap-3 px-2" aria-label={t('shell.accountMenu')}>
            {avatar}
            <span className="flex min-w-0 flex-1 flex-col text-left leading-tight">
              <span className="truncate text-sm font-semibold">{displayName}</span>
              <span className="text-muted-foreground truncate text-xs font-normal">{user.email}</span>
            </span>
            <ChevronsUpDown className="text-muted-foreground" />
          </Button>
        ) : (
          <Button variant="ghost" size="icon" className="rounded-full" aria-label={t('shell.accountMenu')}>
            {avatar}
          </Button>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent align={isSidebar ? 'start' : 'end'} side={isSidebar ? 'top' : 'bottom'} className="w-60">
        <DropdownMenuLabel className="text-muted-foreground truncate font-normal">{user.email}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/me/profile">
            <User />
            {t('shell.profile')}
          </Link>
        </DropdownMenuItem>
        {!isSidebar && hasRole(user.roles, 'ORGANIZER') && (
          <DropdownMenuItem asChild>
            <Link to="/organizer">{t('shell.organizer.roleName')}</Link>
          </DropdownMenuItem>
        )}
        {!isSidebar && hasRole(user.roles, 'ADMIN') && (
          <DropdownMenuItem asChild>
            <Link to="/admin">{t('shell.admin.roleName')}</Link>
          </DropdownMenuItem>
        )}
        {isSidebar && (
          <DropdownMenuItem asChild>
            <Link to="/">
              <Globe />
              {t('shell.backToStore')}
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void logout()}>
          <LogOut />
          {t('auth.logout')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
