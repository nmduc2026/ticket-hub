import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { PublicTier, SeatMapSectionState, SeatState } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { TIER_COLOR_COUNT } from '@/features/seat-map'
import { HOLD_MINUTES } from '@/lib/config'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/utils'

interface Props {
  tiers: PublicTier[]
  sections: SeatMapSectionState[]
  selectedSeats: SeatState[]
  maxSelect: number
  limitReached: boolean
  canContinue: boolean
  isPending: boolean
  onRemove: (seatId: string) => void
  onContinue: () => void
}

/** Cột phải màn chọn ghế (#5): hạng vé, ghế đã chọn kèm nút bỏ chọn, tạm tính và nút Tiếp tục. */
export function SelectionPanel({
  tiers,
  sections,
  selectedSeats,
  maxSelect,
  limitReached,
  canContinue,
  isPending,
  onRemove,
  onContinue,
}: Props) {
  const { t } = useTranslation()
  const tierBySection = new Map(sections.map((section) => [section.code, section.tierId]))
  const tierOf = (seat: SeatState) => tiers.find((tier) => tier.id === tierBySection.get(seat.sectionCode))
  const currency = tiers[0]?.currency ?? 'VND'
  const total = selectedSeats.reduce((sum, seat) => sum + (tierOf(seat)?.priceMinor ?? 0), 0)

  return (
    <aside className="bg-card flex flex-col gap-5 rounded-xl border p-4 lg:sticky lg:top-20">
      <section aria-labelledby="tiers-heading" className="flex flex-col gap-2">
        <h2 id="tiers-heading" className="text-sm font-semibold">
          {t('seatPage.tiers')}
        </h2>
        <ul className="flex flex-col gap-1.5">
          {tiers.map((tier, index) => (
            <li key={tier.id} className="flex items-center gap-2 text-sm">
              <span
                className="size-3 shrink-0 rounded-sm"
                style={{ backgroundColor: `var(--tier-${(index % TIER_COLOR_COUNT) + 1})` }}
                aria-hidden="true"
              />
              <span className="flex-1 truncate font-medium">{tier.name}</span>
              <span className="text-muted-foreground whitespace-nowrap">
                {tier.available > 0 ? t('seatPage.left', { count: tier.available }) : t('eventDetail.tierSoldOut')}
              </span>
              <span className="whitespace-nowrap">{formatMoney(tier.priceMinor, tier.currency)}</span>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="selected-heading" className="flex flex-col gap-2">
        <div className="flex items-baseline justify-between">
          <h2 id="selected-heading" className="text-sm font-semibold">
            {t('seatPage.selectedSeats')}
          </h2>
          <span className="text-muted-foreground text-sm">{t('seatPage.count', { count: selectedSeats.length })}</span>
        </div>
        {selectedSeats.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('seatPage.emptySelection')}</p>
        ) : (
          <ul className="flex flex-col gap-1">
            {selectedSeats.map((seat) => {
              const tier = tierOf(seat)
              const label = t('seatMap.seat', { row: seat.row, number: seat.number })
              return (
                <li key={seat.id} className="flex items-center gap-2 text-sm">
                  <span className="flex-1">
                    {label}
                    {tier && <span className="text-muted-foreground"> · {tier.name}</span>}
                  </span>
                  {tier && <span className="whitespace-nowrap">{formatMoney(tier.priceMinor, tier.currency)}</span>}
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t('seatPage.remove', { seat: label })}
                    onClick={() => onRemove(seat.id)}
                  >
                    <X aria-hidden="true" />
                  </Button>
                </li>
              )
            })}
          </ul>
        )}
        <p className={cn('text-xs', limitReached ? 'text-destructive font-medium' : 'text-muted-foreground')}>
          {t('seatPage.maxNote', { max: maxSelect })}
        </p>
      </section>

      <div className="flex items-baseline justify-between border-t pt-4">
        <span className="text-muted-foreground text-sm">{t('seatPage.subtotal')}</span>
        <strong className="text-lg whitespace-nowrap">{formatMoney(total, currency)}</strong>
      </div>
      <div className="flex flex-col gap-2">
        <Button disabled={!canContinue || isPending} onClick={onContinue}>
          {t('seatPage.continue')}
        </Button>
        <p className="text-muted-foreground text-center text-xs">{t('seatPage.holdNote', { minutes: HOLD_MINUTES })}</p>
      </div>
    </aside>
  )
}
