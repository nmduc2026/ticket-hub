import { useQueryClient } from '@tanstack/react-query'
import { MoreHorizontal, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useDeleteTicketTier } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import type { OrganizerEventDetail, TicketTier } from '@/api/generated/model'
import { type Column, DataTable } from '@/components/list/DataTable'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { TIER_COLOR_COUNT } from '@/features/seat-map'
import { errorMessage } from '@/lib/error-messages'
import { formatMoney } from '@/lib/money'

import { invalidateOrganizerEvent } from './eventRequest'
import { SectionMapPreview } from './SectionMapPreview'
import { TierDialog } from './TierDialog'
import { useEventSeatMap } from './useEventSeatMap'
import { useOrganizerEvent } from './useOrganizerEvent'

interface TierRow {
  tier: TicketTier
  sectionNames: string[]
  seatCount: number
  color: string
}

/** Tab "Hạng vé & giá" (màn #13): bảng hạng vé, gán khu, xem trước sơ đồ; chỉ sửa được khi còn là nháp (BR-14). */
export function TiersTab({ eventId }: { eventId: string }) {
  const { event, isLoading } = useOrganizerEvent(eventId)
  if (isLoading) return <Skeleton className="h-96" aria-busy="true" />
  if (!event) return null
  return <Tiers event={event} />
}

function Tiers({ event }: { event: OrganizerEventDetail }) {
  const { t } = useTranslation()
  const seatMap = useEventSeatMap(event)
  const locked = event.salesInfoLocked
  const [editing, setEditing] = useState<TicketTier | 'new' | null>(null)
  const [deleting, setDeleting] = useState<TicketTier | null>(null)

  const rows: TierRow[] = useMemo(() => {
    const sections = new Map((seatMap?.sections ?? []).map((section) => [section.id, section]))
    return event.tiers.map((tier, index) => {
      const assigned = event.tierSections
        .filter((assignment) => assignment.tierId === tier.id)
        .map((assignment) => sections.get(assignment.sectionId))
        .filter((section) => section !== undefined)
      return {
        tier,
        sectionNames: assigned.map((section) => section.name),
        seatCount: assigned.reduce((sum, section) => sum + section.seatCount, 0),
        color: `var(--tier-${(index % TIER_COLOR_COUNT) + 1})`,
      }
    })
  }, [event.tiers, event.tierSections, seatMap])

  const columns: Column<TierRow>[] = [
    {
      id: 'name',
      header: t('organizerEvents.tiers.name'),
      cell: (row) => (
        <span className="flex items-center gap-2 font-medium">
          <span className="size-3 shrink-0 rounded-sm" style={{ backgroundColor: row.color }} aria-hidden="true" />
          {row.tier.name}
        </span>
      ),
    },
    {
      id: 'sections',
      header: t('organizerEvents.tiers.sections'),
      cell: (row) => (row.sectionNames.length > 0 ? row.sectionNames.join(', ') : <span className="text-muted-foreground">{t('organizerEvents.tiers.noSections')}</span>),
    },
    { id: 'seats', header: t('organizerEvents.tiers.seats'), className: 'whitespace-nowrap', cell: (row) => row.seatCount },
    { id: 'price', header: t('organizerEvents.tiers.price'), className: 'whitespace-nowrap', cell: (row) => formatMoney(row.tier.priceMinor, row.tier.currency) },
  ]

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-0.5">
          <h2 className="text-base font-semibold">{t('organizerEvents.tiers.title')}</h2>
          <p className="text-muted-foreground text-sm">{locked ? t('organizerEvents.form.lockedHint') : t('organizerEvents.tiers.hint')}</p>
        </div>
        {!locked && (
          <Button size="sm" onClick={() => setEditing('new')}>
            <Plus aria-hidden="true" />
            {t('organizerEvents.tiers.add')}
          </Button>
        )}
      </div>

      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.tier.id}
        getRowLabel={(row) => row.tier.name}
        isLoading={false}
        isError={false}
        onRetry={() => {}}
        hasFilters={false}
        onResetFilters={() => {}}
        emptyTitle={t('organizerEvents.tiers.emptyTitle')}
        emptyBody={locked ? undefined : t('organizerEvents.tiers.emptyBody')}
        actionsHeader={locked ? undefined : t('organizerEvents.tiers.actions')}
        rowActions={
          locked
            ? undefined
            : (row) => (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="icon-sm" aria-label={t('organizerEvents.tiers.rowMenu', { name: row.tier.name })}>
                      <MoreHorizontal aria-hidden="true" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onSelect={() => setEditing(row.tier)}>{t('organizerEvents.tiers.edit')}</DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem variant="destructive" onSelect={() => setDeleting(row.tier)}>
                      {t('organizerEvents.tiers.delete')}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              )
        }
      />

      {seatMap && (
        <section aria-labelledby="preview-heading" className="flex max-w-3xl flex-col gap-2">
          <h2 id="preview-heading" className="text-base font-semibold">
            {t('organizerEvents.tiers.preview')}
          </h2>
          <SectionMapPreview
            seatMap={seatMap}
            tierBySection={new Map(event.tierSections.map((assignment) => [assignment.sectionId, assignment.tierId]))}
            tierIds={event.tiers.map((tier) => tier.id)}
          />
        </section>
      )}

      {editing !== null && (
        <TierDialog
          // Mỗi lần mở là một form mới, không giữ giá trị lần trước.
          key={editing === 'new' ? 'new' : editing.id}
          event={event}
          sections={seatMap?.sections ?? []}
          tier={editing === 'new' ? undefined : editing}
          open
          onOpenChange={(open) => !open && setEditing(null)}
        />
      )}
      <DeleteTierDialog eventId={event.id} tier={deleting} onClose={() => setDeleting(null)} />
    </div>
  )
}

function DeleteTierDialog({ eventId, tier, onClose }: { eventId: string; tier: TicketTier | null; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const remove = useDeleteTicketTier()

  return (
    <Dialog
      open={tier !== null}
      onOpenChange={(open) => {
        if (!open) {
          remove.reset()
          onClose()
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('organizerEvents.tiers.deleteTitle', { name: tier?.name })}</DialogTitle>
          <DialogDescription>{t('organizerEvents.tiers.deleteBody')}</DialogDescription>
        </DialogHeader>
        {remove.isError && (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(remove.error, t)}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="destructive"
            disabled={remove.isPending}
            onClick={() => {
              if (!tier) return
              remove.mutate(
                { eventId, tierId: tier.id },
                {
                  onSuccess: () => {
                    void invalidateOrganizerEvent(queryClient, eventId)
                    onClose()
                  },
                },
              )
            }}
          >
            {t('organizerEvents.tiers.deleteConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
