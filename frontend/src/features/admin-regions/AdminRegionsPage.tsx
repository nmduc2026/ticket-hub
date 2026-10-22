import { MoreHorizontal, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useListAdminAdministrativeUnits } from '@/api/generated/endpoints/admin-administrative-unit-controller/admin-administrative-unit-controller'
import type { AdminAdministrativeUnit } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { FacetFilter } from '@/components/list/FacetFilter'
import { ListToolbar } from '@/components/list/ListToolbar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError } from '@/lib/api-client'

import { DisableUnitDialog } from './DisableUnitDialog'
import { UnitDialog } from './UnitDialog'

const LEVELS = [
  { value: '1', label: 'level1' },
  { value: '2', label: 'level2' },
]

/** Đơn vị hành chính của Admin (màn #33): CRUD, tắt (không xoá cứng). */
export function AdminRegionsPage() {
  const { t } = useTranslation()
  const [level, setLevel] = useState<string | undefined>()
  const [q, setQ] = useState('')
  const [editing, setEditing] = useState<AdminAdministrativeUnit | undefined>()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [disabling, setDisabling] = useState<AdminAdministrativeUnit | undefined>()

  const units = useListAdminAdministrativeUnits(level ? { level: Number(level) } : undefined)
  const data = units.data?.status === 200 ? units.data.data : []
  const byId = useMemo(() => new Map(data.map((unit) => [unit.id, unit])), [data])
  const query = q.trim().toLowerCase()
  const rows = units.data ? data.filter((row) => query === '' || row.name.toLowerCase().includes(query) || row.code.toLowerCase().includes(query)) : undefined

  const columns: Column<AdminAdministrativeUnit>[] = useMemo(
    () => [
      { id: 'code', header: t('adminRegions.colCode'), className: 'font-mono text-xs', cell: (row) => row.code },
      { id: 'name', header: t('adminRegions.colName'), cell: (row) => <span className="font-medium">{row.name}</span> },
      { id: 'type', header: t('adminRegions.colType'), cell: (row) => t(`adminRegions.typeNames.${row.type}`) },
      { id: 'parent', header: t('adminRegions.colParent'), cell: (row) => (row.parentId ? byId.get(row.parentId)?.name ?? '—' : '—') },
      { id: 'active', header: t('adminRegions.colStatus'), cell: (row) => <Badge variant={row.active ? 'default' : 'outline'}>{row.active ? t('adminRegions.active') : t('adminRegions.inactive')}</Badge> },
    ],
    [t, byId],
  )

  const error = units.error instanceof ApiError ? units.error : undefined

  return (
    <div className="flex flex-col">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminRegions.title')}</h1>
      <ListToolbar
        search={{ value: q, onChange: setQ, placeholder: t('adminRegions.searchPlaceholder') }}
        filters={
          <FacetFilter mode="single" label={t('adminRegions.colLevel')} options={LEVELS.map((item) => ({ value: item.value, label: t(`adminRegions.${item.label}`) }))} value={level} onChange={setLevel} />
        }
        hasFilters={Boolean(q || level)}
        onReset={() => { setQ(''); setLevel(undefined) }}
        selectedCount={0}
        primaryAction={
          <Button size="sm" onClick={() => { setEditing(undefined); setDialogOpen(true) }}>
            <Plus aria-hidden="true" />
            {t('adminRegions.createButton')}
          </Button>
        }
      />
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.name}
        isLoading={units.isPending}
        isError={units.isError}
        errorDetail={error && error.status >= 500 && error.traceId ? t('list.loadErrorTrace', { status: error.status, traceId: error.traceId }) : undefined}
        onRetry={() => void units.refetch()}
        hasFilters={Boolean(q || level)}
        onResetFilters={() => { setQ(''); setLevel(undefined) }}
        emptyTitle={t('adminRegions.emptyTitle')}
        actionsHeader={t('adminRegions.actionsHeader')}
        rowActions={(row) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.rowMenu', { name: row.name })}>
                <MoreHorizontal aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => { setEditing(row); setDialogOpen(true) }}>{t('adminRegions.edit')}</DropdownMenuItem>
              {row.active && (
                <DropdownMenuItem variant="destructive" onSelect={() => setDisabling(row)}>
                  {t('adminRegions.disable')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      />
      <UnitDialog open={dialogOpen} unit={editing} onClose={() => setDialogOpen(false)} />
      <DisableUnitDialog unit={disabling} onClose={() => setDisabling(undefined)} />
    </div>
  )
}
