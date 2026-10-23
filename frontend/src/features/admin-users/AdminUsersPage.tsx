import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { MoreHorizontal } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  getListAdminUsersQueryKey,
  useListAdminUsers,
  useLockUser,
  useUnlockUser,
} from '@/api/generated/endpoints/admin-user-controller/admin-user-controller'
import type { AdminUser } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'
import { formatDate, formatTime } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

import type { AdminUserSearch } from './adminUserSearch'
import { LockDialog } from './LockDialog'
import { UserSheet } from './UserSheet'

const DEFAULT_PAGE_SIZE = 10
const ROLES = ['CUSTOMER', 'ORGANIZER', 'ADMIN'] as const

/** Người dùng của Admin (màn #28): tìm, lọc theo vai trò, xem chi tiết, khoá / mở khoá có hộp xác nhận. */
export function AdminUsersPage({ search }: { search: AdminUserSearch }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/admin/users' })
  const queryClient = useQueryClient()
  const [hiddenColumns, setHiddenColumns] = useState<ReadonlySet<string>>(new Set())
  const [confirming, setConfirming] = useState<AdminUser | undefined>()

  const page = search.page ?? 0
  const size = search.size ?? DEFAULT_PAGE_SIZE
  const users = useListAdminUsers({ q: search.q, role: search.role, page, size })
  const lock = useLockUser()
  const unlock = useUnlockUser()
  const data = users.data?.status === 200 ? users.data.data : undefined

  const columns: Column<AdminUser>[] = useMemo(
    () => [
      { id: 'name', header: t('adminUsers.colName'), cell: (row) => <span className="font-medium">{row.fullName ?? '—'}</span> },
      { id: 'email', header: t('adminUsers.colEmail'), cell: (row) => row.email ?? '—' },
      {
        id: 'roles',
        header: t('adminUsers.colRoles'),
        cell: (row) => row.roles.map((role) => t(`adminUsers.roleNames.${role}`, { defaultValue: role })).join(', '),
      },
      {
        id: 'lastLoginAt',
        header: t('adminUsers.colLastLogin'),
        className: 'whitespace-nowrap',
        cell: (row) => (row.lastLoginAt ? `${formatDate(row.lastLoginAt)} · ${formatTime(row.lastLoginAt)}` : t('adminUsers.never')),
      },
      {
        id: 'status',
        header: t('adminUsers.colStatus'),
        cell: (row) => (
          <Badge variant={row.enabled ? 'outline' : 'destructive'}>{t(row.enabled ? 'adminUsers.active' : 'adminUsers.locked')}</Badge>
        ),
      },
    ],
    [t],
  )

  const update = (changes: Partial<AdminUserSearch>) =>
    void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  const hasFilters = Boolean(search.q || search.role)

  const confirm = (user: AdminUser) => {
    const mutation = user.enabled ? lock : unlock
    mutation.mutate(
      { id: user.userId },
      {
        onSuccess: () => {
          toast.success(t(user.enabled ? 'adminUsers.locked_ok' : 'adminUsers.unlocked_ok'))
          setConfirming(undefined)
          void queryClient.invalidateQueries({ queryKey: getListAdminUsersQueryKey() })
        },
        onError: (error) => {
          toast.error(errorMessage(error, t))
          setConfirming(undefined)
        },
      },
    )
  }

  const error = users.error instanceof ApiError ? users.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminUsers.title')}</h1>
      <ListToolbar
        search={{
          value: search.q ?? '',
          onChange: (q) => update({ q: q || undefined }),
          placeholder: t('adminUsers.searchPlaceholder'),
        }}
        filters={
          <FacetFilter
            mode="single"
            label={t('adminUsers.role')}
            options={ROLES.map((value) => ({ value, label: t(`adminUsers.roleNames.${value}`) }))}
            value={search.role}
            onChange={(value) => update({ role: value as AdminUserSearch['role'] })}
          />
        }
        hasFilters={hasFilters}
        onReset={() => void navigate({ search: {} })}
        columns={{
          items: columns.map((column) => ({ id: column.id, label: column.header })),
          hidden: hiddenColumns,
          onToggle: (id) =>
            setHiddenColumns((previous) => {
              const next = new Set(previous)
              if (!next.delete(id)) next.add(id)
              return next
            }),
        }}
        selectedCount={0}
      />
      <DataTable
        columns={columns}
        hiddenColumns={hiddenColumns}
        rows={data?.items}
        getRowId={(row) => row.userId}
        getRowLabel={(row) => row.fullName ?? row.email ?? row.userId}
        isLoading={users.isPending}
        isError={users.isError}
        errorDetail={
          error && error.status >= 500 && error.traceId
            ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId })
            : undefined
        }
        onRetry={() => void users.refetch()}
        hasFilters={hasFilters}
        onResetFilters={() => void navigate({ search: {} })}
        emptyTitle={t('adminUsers.emptyTitle')}
        actionsHeader={t('adminUsers.actions')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.rowMenu', { name: row.fullName ?? row.email })}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => void navigate({ search: (previous) => ({ ...previous, view: row.userId }) })}>
                {t('adminUsers.view')}
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => setConfirming(row)}>{t(row.enabled ? 'adminUsers.lock' : 'adminUsers.unlock')}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
        pagination={{
          page,
          size,
          total: data?.total ?? 0,
          onPageChange: (next) => void navigate({ search: (previous) => ({ ...previous, page: next || undefined }) }),
          onSizeChange: (next) => update({ size: next === DEFAULT_PAGE_SIZE ? undefined : next }),
        }}
      />
      <UserSheet userId={search.view} onClose={() => void navigate({ search: (previous) => ({ ...previous, view: undefined }) })} />
      <LockDialog
        user={confirming}
        pending={lock.isPending || unlock.isPending}
        onCancel={() => setConfirming(undefined)}
        onConfirm={confirm}
      />
    </div>
  )
}
