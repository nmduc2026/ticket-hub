import type { FileRouteTypes } from '@/routeTree.gen'
import { LayoutDashboard, type LucideIcon } from 'lucide-react'

export type ShellVariant = 'admin' | 'organizer'

export interface NavItem {
  to: FileRouteTypes['to']
  labelKey: string
  icon: LucideIcon
}

export interface NavGroup {
  headingKey: string
  items: NavItem[]
}

export interface ShellConfig {
  /** Trang Tổng quan của vai trò: đích của mục đầu tiên trong breadcrumb. */
  home: FileRouteTypes['to']
  navLabelKey: string
  subtitleKey: string
  roleNameKey: string
  groups: NavGroup[]
}

/**
 * Chỉ khai báo mục có route thật; mục của màn chưa làm được thêm vào đây khi route tương ứng ra đời
 * (docs/design/README.md §4: màn chưa tới sprint thì ẩn).
 */
export const SHELL_CONFIG: Record<ShellVariant, ShellConfig> = {
  admin: {
    home: '/admin',
    navLabelKey: 'shell.admin.navLabel',
    subtitleKey: 'shell.admin.subtitle',
    roleNameKey: 'shell.admin.roleName',
    groups: [
      {
        headingKey: 'shell.admin.operations',
        items: [{ to: '/admin', labelKey: 'shell.overview', icon: LayoutDashboard }],
      },
    ],
  },
  organizer: {
    home: '/organizer',
    navLabelKey: 'shell.organizer.navLabel',
    subtitleKey: 'shell.organizer.subtitle',
    roleNameKey: 'shell.organizer.roleName',
    groups: [
      {
        headingKey: 'shell.organizer.selling',
        items: [{ to: '/organizer', labelKey: 'shell.overview', icon: LayoutDashboard }],
      },
    ],
  },
}
