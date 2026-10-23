import { useNavigate } from '@tanstack/react-router'
import { MoreHorizontal } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getListAdminAuditLogsUrl, useListAdminAuditLogs } from '@/api/generated/endpoints/admin-audit-log-controller/admin-audit-log-controller'
import { useListAdminUsers } from '@/api/generated/endpoints/admin-user-controller/admin-user-controller'
import { AdminAuditLogRowAction } from '@/api/generated/model'
import type { AdminAuditLogRow } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { DateRangeFilter } from '@/components/list/DateRangeFilter'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'
import { downloadServerCsv } from '@/lib/csv'
import { formatDate, formatTime, vnInputsToIso } from '@/lib/date'

import { AuditLogSheet } from './AuditLogSheet'
import type { AdminAuditLogSearch } from './adminAuditLogSearch'

const DEFAULT_PAGE_SIZE = 20
const ACTIONS = Object.values(AdminAuditLogRowAction)

/** Nhật ký thao tác quản trị (màn #31): lọc theo người thao tác / hành động / thời gian, so sánh trước / sau. */
export function AdminAuditLogsPage({ search }: { search: AdminAuditLogSearch }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/admin/audit-logs' })
  const [viewing, setViewing] = useState<AdminAuditLogRow | undefined>()

  const page = search.page ?? 0
  const size = search.size ?? DEFAULT_PAGE_SIZE
  const from = vnInputsToIso(search.from ?? '', '00:00')
  const to = search.to ? vnInputsToIso(search.to, '00:00') : undefined
  const action = search.action as AdminAuditLogRowAction | undefined
  const logs = useListAdminAuditLogs({ actor: search.actor, action, from, to, page, size }, { query: { retry: false } })
  const admins = useListAdminUsers({ role: 'ADMIN', size: 100 }, { query: { staleTime: 5 * 60_000 } })

  const data = logs.data?.status === 200 ? logs.data.data : undefined
  const adminList = admins.data?.status === 200 ? admins.data.data.items : []
  const q = (search.q ?? '').trim().toLowerCase()
  const rows = data?.items?.filter((row) => q === '' || row.actorName.toLowerCase().includes(q) || row.targetType.toLowerCase().includes(q))

  const columns: Column<AdminAuditLogRow>[] = useMemo(
    () => [
      { id: 'actorName', header: t('adminAuditLogs.colActor'), cell: (row) => <span className="font-medium">{row.actorName}</span> },
      { id: 'action', header: t('adminAuditLogs.colAction'), cell: (row) => t(`adminAuditLogs.actions.${row.action}`, { defaultValue: row.action }) },
      { id: 'targetType', header: t('adminAuditLogs.colTarget'), cell: (row) => row.targetType },
      { id: 'createdAt', header: t('adminAuditLogs.colTime'), className: 'whitespace-nowrap', cell: (row) => `${formatDate(row.createdAt)} · ${formatTime(row.createdAt)}` },
    ],
    [t],
  )

  const update = (changes: Partial<AdminAuditLogSearch>) => void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  const hasFilters = Boolean(search.q || search.actor || search.action || search.from || search.to)
  const resetFilters = () => void navigate({ search: {} })

  const exportRows = () => {
    const url = getListAdminAuditLogsUrl({ actor: search.actor, action, from, to })
    downloadServerCsv(url, 'audit-log.csv').catch((error: unknown) => toast.error(error instanceof ApiError ? error.message : t('list.loadError')))
  }

  const error = logs.error instanceof ApiError ? logs.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminAuditLogs.title')}</h1>
      <ListToolbar
        search={{ value: search.q ?? '', onChange: (value) => update({ q: value || undefined }), placeholder: t('adminAuditLogs.searchPlaceholder') }}
        filters={
          <>
            <FacetFilter mode="single" label={t('adminAuditLogs.colActor')} options={adminList.map((admin) => ({ value: admin.userId, label: admin.fullName ?? admin.email ?? admin.userId }))} value={search.actor} onChange={(value) => update({ actor: value })} />
            <FacetFilter mode="single" label={t('adminAuditLogs.colAction')} options={ACTIONS.map((value) => ({ value, label: t(`adminAuditLogs.actions.${value}`, { defaultValue: value }) }))} value={search.action} onChange={(value) => update({ action: value as AdminAuditLogSearch['action'] })} />
            <DateRangeFilter label={t('adminAuditLogs.colTime')} value={{ from: search.from, to: search.to }} onChange={(value) => update({ from: value.from, to: value.to })} />
          </>
        }
        hasFilters={hasFilters}
        onReset={resetFilters}
        selectedCount={0}
        onExport={exportRows}
      />
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.action}
        isLoading={logs.isPending}
        isError={logs.isError}
        errorDetail={error && error.status >= 500 && error.traceId ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId }) : undefined}
        onRetry={() => void logs.refetch()}
        hasFilters={hasFilters}
        onResetFilters={resetFilters}
        emptyTitle={t('adminAuditLogs.empty')}
        actionsHeader={t('adminAuditLogs.actionsHeader')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('adminAuditLogs.viewRow')}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => setViewing(row)}>{t('adminAuditLogs.view')}</DropdownMenuItem>
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
      <AuditLogSheet row={viewing} onClose={() => setViewing(undefined)} />
    </div>
  )
}
