import { useQueryClient } from '@tanstack/react-query'
import { MoreHorizontal, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getListAdminVenuesQueryKey, useBulkDeleteVenues, useListAdminVenues } from '@/api/generated/endpoints/admin-venue-controller/admin-venue-controller'
import type { AdminVenue } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { ExcelMenu } from '@/components/list/ExcelMenu'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'
import { errorMessage } from '@/lib/error-messages'

import { VenueDialog } from './VenueDialog'

/** Địa điểm của Admin (màn #16): CRUD, xoá nhiều (chỉ địa điểm chưa có sự kiện). */
export function AdminVenuesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
  const [editing, setEditing] = useState<AdminVenue | undefined>()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [q, setQ] = useState('')

  const venues = useListAdminVenues()
  const bulkDelete = useBulkDeleteVenues()

  const data = venues.data?.status === 200 ? venues.data.data : []
  const query = q.trim().toLowerCase()
  const rows = venues.data ? data.filter((row) => query === '' || row.name.toLowerCase().includes(query) || row.address.toLowerCase().includes(query)) : undefined

  const refresh = () => {
    setSelected(new Set())
    void queryClient.invalidateQueries({ queryKey: getListAdminVenuesQueryKey() })
  }

  const columns: Column<AdminVenue>[] = useMemo(
    () => [
      { id: 'name', header: t('adminVenues.colName'), cell: (row) => <span className="font-medium">{row.name}</span> },
      { id: 'address', header: t('adminVenues.colAddress'), cell: (row) => row.address },
      { id: 'ward', header: t('adminVenues.colWard'), cell: (row) => `${row.wardName}, ${row.cityName}` },
      { id: 'capacity', header: t('adminVenues.colCapacity'), className: 'text-right', cell: (row) => row.capacity },
    ],
    [t],
  )

  const deleteSelected = () =>
    bulkDelete.mutate(
      { data: { ids: [...selected] } },
      {
        onSuccess: (response) => {
          if (response.status !== 200) return
          const failed = response.data.results.filter((item) => !item.ok)
          if (failed.length > 0) toast.error(t('adminVenues.deleteFailedCount', { count: failed.length }))
          else toast.success(t('adminVenues.deleted'))
          refresh()
        },
        onError: (error) => toast.error(errorMessage(error, t)),
      },
    )

  const error = venues.error instanceof ApiError ? venues.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminVenues.title')}</h1>
      <ListToolbar
        search={{ value: q, onChange: setQ, placeholder: t('adminVenues.searchPlaceholder') }}
        hasFilters={false}
        onReset={() => setQ('')}
        excelMenu={<ExcelMenu type="venues" />}
        selectedCount={selected.size}
        bulkActions={
          selected.size > 0 && (
            <Button size="sm" variant="destructive" disabled={bulkDelete.isPending} onClick={deleteSelected}>
              {t('adminVenues.deleteSelected', { count: selected.size })}
            </Button>
          )
        }
        primaryAction={
          <Button size="sm" onClick={() => { setEditing(undefined); setDialogOpen(true) }}>
            <Plus aria-hidden="true" />
            {t('adminVenues.createButton')}
          </Button>
        }
      />
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.name}
        isLoading={venues.isPending}
        isError={venues.isError}
        errorDetail={error && error.status >= 500 && error.traceId ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId }) : undefined}
        onRetry={() => void venues.refetch()}
        hasFilters={q !== ''}
        onResetFilters={() => setQ('')}
        emptyTitle={t('adminVenues.emptyTitle')}
        selection={{ selected, onChange: setSelected }}
        actionsHeader={t('adminVenues.actions')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.rowMenu', { name: row.name })}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => { setEditing(row); setDialogOpen(true) }}>{t('adminVenues.edit')}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      />
      <VenueDialog open={dialogOpen} venue={editing} onClose={() => setDialogOpen(false)} />
    </div>
  )
}
