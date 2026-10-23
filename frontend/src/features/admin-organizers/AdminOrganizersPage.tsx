import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { MoreHorizontal } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  getListAdminOrganizersQueryKey,
  useApproveOrganizer,
  useBulkApproveOrganizers,
  useListAdminOrganizers,
} from '@/api/generated/endpoints/admin-organizer-controller/admin-organizer-controller'
import type { AdminOrganizer, ListAdminOrganizersStatus } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'
import { formatDate, formatTime } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

import type { AdminOrganizerSearch } from './adminOrganizerSearch'
import { OrganizerSheet } from './OrganizerSheet'
import { RejectDialog } from './RejectDialog'

const DEFAULT_PAGE_SIZE = 10
const STATUSES: ListAdminOrganizersStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED']

/** Hồ sơ organizer của Admin (màn #15): lọc theo trạng thái, xem, duyệt, từ chối kèm lý do, duyệt hàng loạt. */
export function AdminOrganizersPage({ search }: { search: AdminOrganizerSearch }) {
  const { t } = useTranslation()
  const navigate = useNavigate({ from: '/admin/organizers' })
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
  const [hiddenColumns, setHiddenColumns] = useState<ReadonlySet<string>>(new Set())
  const [rejecting, setRejecting] = useState<AdminOrganizer | undefined>()

  const page = search.page ?? 0
  const size = search.size ?? DEFAULT_PAGE_SIZE
  const status = search.status as ListAdminOrganizersStatus | undefined
  const organizers = useListAdminOrganizers({ status, page, size })
  const approve = useApproveOrganizer()
  const bulkApprove = useBulkApproveOrganizers()

  const data = organizers.data?.status === 200 ? organizers.data.data : undefined
  // API chưa có tìm theo chữ: lọc trên trang đang hiện.
  const query = (search.q ?? '').trim().toLowerCase()
  const rows = data?.items.filter(
    (row) => query === '' || row.displayName.toLowerCase().includes(query) || (row.email ?? '').toLowerCase().includes(query),
  )
  const viewing = data?.items.find((row) => row.id === search.view)

  const refresh = () => {
    setSelected(new Set())
    void queryClient.invalidateQueries({ queryKey: getListAdminOrganizersQueryKey() })
  }

  const columns: Column<AdminOrganizer>[] = useMemo(
    () => [
      { id: 'name', header: t('adminOrganizers.colName'), cell: (row) => <span className="font-medium">{row.displayName}</span> },
      { id: 'email', header: t('adminOrganizers.colEmail'), cell: (row) => row.email ?? '—' },
      { id: 'type', header: t('adminOrganizers.colType'), cell: (row) => (row.type ? t(`adminOrganizers.typeNames.${row.type}`) : '—') },
      {
        id: 'submittedAt',
        header: t('adminOrganizers.colSubmitted'),
        className: 'whitespace-nowrap',
        cell: (row) => `${formatDate(row.submittedAt)} · ${formatTime(row.submittedAt)}`,
      },
      {
        id: 'status',
        header: t('adminOrganizers.colStatus'),
        cell: (row) => <Badge variant={row.status === 'PENDING' ? 'default' : 'outline'}>{t(`adminOrganizers.statusNames.${row.status}`)}</Badge>,
      },
    ],
    [t],
  )

  const update = (changes: Partial<AdminOrganizerSearch>) => {
    setSelected(new Set())
    void navigate({ search: (previous) => ({ ...previous, ...changes, page: undefined }) })
  }
  const hasFilters = Boolean(search.q || search.status)
  const resetFilters = () => {
    setSelected(new Set())
    void navigate({ search: {} })
  }

  const approveOne = (organizer: AdminOrganizer) =>
    approve.mutate(
      { id: organizer.id },
      {
        onSuccess: () => {
          toast.success(t('adminOrganizers.approved'))
          refresh()
        },
        onError: (error) => toast.error(errorMessage(error, t)),
      },
    )

  const approveSelected = () =>
    bulkApprove.mutate(
      { data: { ids: [...selected] } },
      {
        onSuccess: (response) => {
          if (response.status !== 200) return
          toast.success(t('adminOrganizers.bulkApproved', { count: response.data.approved.length }))
          if (response.data.skipped.length > 0) {
            toast.info(t('adminOrganizers.bulkSkipped', { count: response.data.skipped.length }))
          }
          refresh()
        },
        onError: (error) => toast.error(errorMessage(error, t)),
      },
    )

  const error = organizers.error instanceof ApiError ? organizers.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminOrganizers.title')}</h1>
      <ListToolbar
        search={{
          value: search.q ?? '',
          onChange: (q) => update({ q: q || undefined }),
          placeholder: t('adminOrganizers.searchPlaceholder'),
        }}
        filters={
          <FacetFilter
            mode="single"
            label={t('adminOrganizers.status')}
            options={STATUSES.map((value) => ({ value, label: t(`adminOrganizers.statusNames.${value}`) }))}
            value={search.status}
            onChange={(value) => update({ status: value })}
          />
        }
        hasFilters={hasFilters}
        onReset={resetFilters}
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
        selectedCount={selected.size}
        bulkActions={
          selected.size > 0 && (
            <Button size="sm" disabled={bulkApprove.isPending} onClick={approveSelected}>
              {t('adminOrganizers.approveSelected', { count: selected.size })}
            </Button>
          )
        }
      />
      <DataTable
        columns={columns}
        hiddenColumns={hiddenColumns}
        rows={rows}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.displayName}
        isLoading={organizers.isPending}
        isError={organizers.isError}
        errorDetail={
          error && error.status >= 500 && error.traceId
            ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId })
            : undefined
        }
        onRetry={() => void organizers.refetch()}
        hasFilters={hasFilters}
        onResetFilters={resetFilters}
        emptyTitle={t('adminOrganizers.emptyTitle')}
        selection={{ selected, onChange: setSelected }}
        actionsHeader={t('adminOrganizers.actions')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.rowMenu', { name: row.displayName })}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => void navigate({ search: (previous) => ({ ...previous, view: row.id }) })}>
                {t('adminOrganizers.view')}
              </DropdownMenuItem>
              {row.status === 'PENDING' && (
                <>
                  <DropdownMenuItem onSelect={() => approveOne(row)}>{t('adminOrganizers.approve')}</DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => setRejecting(row)}>{t('adminOrganizers.reject')}</DropdownMenuItem>
                </>
              )}
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
      <OrganizerSheet
        organizer={viewing}
        onClose={() => void navigate({ search: (previous) => ({ ...previous, view: undefined }) })}
      />
      <RejectDialog
        organizer={rejecting}
        onClose={() => setRejecting(undefined)}
        onRejected={() => {
          toast.success(t('adminOrganizers.rejected'))
          refresh()
        }}
      />
    </div>
  )
}
