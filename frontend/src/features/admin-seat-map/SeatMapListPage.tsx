import { Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, MoreHorizontal, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  getListAdminSeatMapsQueryKey,
  useCopyAdminSeatMap,
  useCreateAdminSeatMap,
  useDeleteAdminSeatMap,
  useListAdminSeatMaps,
} from '@/api/generated/endpoints/admin-seat-map-controller/admin-seat-map-controller'
import type { AdminSeatMapSummary } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api-client'
import { errorMessage } from '@/lib/error-messages'

const DEFAULT_WIDTH = 1000
const DEFAULT_HEIGHT = 640

/** Danh sách sơ đồ ghế của một địa điểm (lối vào màn #17): mở editor, tạo mới, tạo bản sao, xoá. */
export function SeatMapListPage({ venueId }: { venueId: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const maps = useListAdminSeatMaps({ venueId })
  const create = useCreateAdminSeatMap()
  const copy = useCopyAdminSeatMap()
  const remove = useDeleteAdminSeatMap()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [deleting, setDeleting] = useState<AdminSeatMapSummary | undefined>()

  const rows = maps.data?.status === 200 ? maps.data.data : undefined
  const refresh = () => void queryClient.invalidateQueries({ queryKey: getListAdminSeatMapsQueryKey({ venueId }) })
  const open = (mapId: string) => void navigate({ to: '/admin/venues/$venueId/seat-maps/$mapId', params: { venueId, mapId } })
  const fail = (error: unknown) => toast.error(errorMessage(error, t))

  const columns: Column<AdminSeatMapSummary>[] = useMemo(
    () => [
      { id: 'name', header: t('adminSeatMap.colName'), cell: (row) => <span className="font-medium">{row.name}</span> },
      { id: 'version', header: t('adminSeatMap.colVersion'), cell: (row) => row.version },
      { id: 'size', header: t('adminSeatMap.colSize'), className: 'whitespace-nowrap', cell: (row) => `${row.width} × ${row.height}` },
      { id: 'sections', header: t('adminSeatMap.colSections'), cell: (row) => row.sectionCount },
      { id: 'seats', header: t('adminSeatMap.colSeats'), cell: (row) => row.seatCount },
      { id: 'status', header: t('adminSeatMap.colStatus'), cell: (row) => (row.locked ? <Badge variant="secondary">{t('adminSeatMap.locked')}</Badge> : <Badge variant="outline">{t('adminSeatMap.editing')}</Badge>) },
    ],
    [t],
  )

  const submitCreate = () => {
    if (name.trim() === '') return
    create.mutate(
      { data: { venueId, name: name.trim(), width: DEFAULT_WIDTH, height: DEFAULT_HEIGHT, sections: [], elements: [] } },
      {
        onSuccess: (response) => {
          if (response.status !== 201) return
          setCreating(false)
          setName('')
          refresh()
          open(response.data.id)
        },
        onError: fail,
      },
    )
  }

  const error = maps.error instanceof ApiError ? maps.error : undefined

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Button asChild variant="ghost" size="icon-sm">
            <Link to="/admin/venues" aria-label={t('adminSeatMap.backToVenues')}>
              <ArrowLeft aria-hidden="true" />
            </Link>
          </Button>
          <h1 className="text-2xl font-semibold tracking-tight">{t('adminSeatMap.listTitle')}</h1>
        </div>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus aria-hidden="true" />
          {t('adminSeatMap.create')}
        </Button>
      </div>
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.name}
        isLoading={maps.isPending}
        isError={maps.isError}
        errorDetail={error && error.status >= 500 && error.traceId ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId }) : undefined}
        onRetry={() => void maps.refetch()}
        hasFilters={false}
        onResetFilters={() => undefined}
        emptyTitle={t('adminSeatMap.emptyTitle')}
        actionsHeader={t('organizerEvents.actions')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.rowMenu', { name: row.name })}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => open(row.id)}>{row.locked ? t('adminSeatMap.view') : t('adminSeatMap.edit')}</DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() =>
                  copy.mutate(
                    { id: row.id },
                    { onSuccess: () => { toast.success(t('adminSeatMap.copied')); refresh() }, onError: fail },
                  )
                }
              >
                {t('adminSeatMap.makeCopy')}
              </DropdownMenuItem>
              <DropdownMenuItem disabled={row.locked} onSelect={() => setDeleting(row)}>
                {t('adminSeatMap.delete')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      />

      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('adminSeatMap.create')}</DialogTitle>
            <DialogDescription>{t('adminSeatMap.createHint')}</DialogDescription>
          </DialogHeader>
          <Input
            autoFocus
            value={name}
            maxLength={120}
            placeholder={t('adminSeatMap.mapName')}
            aria-label={t('adminSeatMap.mapName')}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && submitCreate()}
          />
          <DialogFooter>
            <Button disabled={name.trim() === '' || create.isPending} onClick={submitCreate}>
              {t('adminSeatMap.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleting !== undefined} onOpenChange={(next) => !next && setDeleting(undefined)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('adminSeatMap.delete')}</DialogTitle>
            <DialogDescription>{t('adminSeatMap.deleteConfirm', { name: deleting?.name })}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleting(undefined)}>
              {t('common.close')}
            </Button>
            <Button
              variant="destructive"
              disabled={remove.isPending}
              onClick={() =>
                deleting &&
                remove.mutate(
                  { id: deleting.id },
                  { onSuccess: () => { setDeleting(undefined); refresh() }, onError: fail },
                )
              }
            >
              {t('adminSeatMap.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
