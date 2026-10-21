import { Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import type { BookingSummary } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { errorMessage } from '@/lib/error-messages'
import { formatMoney } from '@/lib/money'

import { useRequestRefund } from './useRequestRefund'

/** Còn hoàn được: đơn đã xác nhận và chưa quá hạn hoàn do server báo (BR-07). */
export function canRefund(booking: BookingSummary, now: number = Date.now()): boolean {
  return booking.status === 'CONFIRMED' && !!booking.refundDeadline && new Date(booking.refundDeadline).getTime() >= now
}

/**
 * Hộp xác nhận hoàn vé (màn #21): nêu rõ số tiền nhận lại và phần phí nền tảng không hoàn. Số tiền do server tính,
 * client chỉ hiển thị. Yêu cầu đã gửi không huỷ được nên nút xác nhận là nút huỷ-diệt (destructive).
 */
export function RefundDialog({
  booking,
  open,
  onOpenChange,
}: {
  booking: BookingSummary
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const refund = useRequestRefund(booking.bookingId, () => {
    toast.success(t('myBookings.refund.sent'))
    onOpenChange(false)
  })
  const code = booking.bookingId.slice(-8).toUpperCase()

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) refund.reset()
        onOpenChange(next)
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('myBookings.refund.title', { code })}</DialogTitle>
          <DialogDescription>{t('myBookings.refund.description')}</DialogDescription>
        </DialogHeader>

        <dl className="flex flex-col gap-1.5 rounded-lg border px-4 py-3 text-sm">
          <Row label={t('myBookings.refund.event')} value={booking.eventTitle} />
          <Row label={t('myBookings.refund.tickets')} value={String(booking.ticketCount)} />
          <Row label={t('myBookings.refund.paid')} value={formatMoney(booking.totalMinor, booking.currency)} />
          <Row
            label={t('myBookings.refund.fee')}
            value={formatMoney(booking.platformFeeMinor, booking.currency)}
          />
          <div className="mt-1 flex justify-between gap-3 border-t pt-2 font-semibold">
            <dt>{t('myBookings.refund.amount')}</dt>
            <dd className="whitespace-nowrap">{formatMoney(booking.refundableAmountMinor ?? 0, booking.currency)}</dd>
          </div>
        </dl>

        <p className="bg-muted text-muted-foreground flex gap-2 rounded-lg p-3 text-sm">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{t('myBookings.refund.notice')}</span>
        </p>

        {refund.isError && (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(refund.error, t)}
          </p>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={refund.isPending}>
            {t('myBookings.refund.keep')}
          </Button>
          <Button variant="destructive" onClick={() => refund.mutate()} disabled={refund.isPending}>
            {t('myBookings.refund.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  )
}
