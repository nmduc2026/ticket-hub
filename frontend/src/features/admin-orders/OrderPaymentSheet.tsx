import { useTranslation } from 'react-i18next'

import { useGetAdminOrderPayment } from '@/api/generated/endpoints/admin-order-payment-controller/admin-order-payment-controller'
import type { AdminBookingRow } from '@/api/generated/model'
import { Badge } from '@/components/ui/badge'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatTime } from '@/lib/date'
import { formatMoney } from '@/lib/money'

const stamp = (iso: string) => `${formatDate(iso)} · ${formatTime(iso)}`

/** Side sheet chi tiết thanh toán của một đơn (màn #29): khoản thu, các lần hoàn và dòng thời gian Stripe. */
export function OrderPaymentSheet({ row, onClose }: { row: AdminBookingRow | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const query = useGetAdminOrderPayment(row?.bookingId ?? '', { query: { enabled: row !== undefined, retry: false } })
  const payment = query.data?.status === 200 ? query.data.data : undefined

  return (
    <Sheet open={row !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{t('adminOrders.sheet.title')}</SheetTitle>
          <SheetDescription>{row ? `${row.eventTitle} · ${row.contactEmail}` : ''}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-4 px-4 pb-4">
          {query.isPending && row && <Skeleton className="h-40 w-full" aria-busy="true" />}
          {query.isError && <p role="alert" className="text-destructive text-sm">{t('adminOrders.sheet.noPayment')}</p>}
          {payment && (
            <>
              <dl className="grid grid-cols-[130px_1fr] gap-x-3 gap-y-1 text-sm">
                <dt className="text-muted-foreground">{t('adminOrders.sheet.status')}</dt>
                <dd><Badge variant="outline">{payment.status}</Badge></dd>
                <dt className="text-muted-foreground">{t('adminOrders.sheet.amount')}</dt>
                <dd>{formatMoney(payment.amountMinor, payment.currency)}</dd>
                <dt className="text-muted-foreground">{t('adminOrders.sheet.fee')}</dt>
                <dd>{formatMoney(payment.applicationFeeMinor, payment.currency)}</dd>
                <dt className="text-muted-foreground">{t('adminOrders.sheet.intent')}</dt>
                <dd className="font-mono text-xs break-all">{payment.stripePaymentIntentId}</dd>
                <dt className="text-muted-foreground">{t('adminOrders.sheet.destination')}</dt>
                <dd className="font-mono text-xs break-all">{payment.destinationAccountId ?? t('adminOrders.sheet.platform')}</dd>
              </dl>

              {payment.refunds.length > 0 && (
                <section className="flex flex-col gap-1">
                  <h3 className="text-sm font-medium">{t('adminOrders.sheet.refunds')}</h3>
                  <ul className="flex flex-col gap-1 text-sm">
                    {payment.refunds.map((refund) => (
                      <li key={refund.createdAt} className="flex flex-wrap items-center gap-2 rounded-md border p-2">
                        <Badge variant={refund.status === 'FAILED' ? 'destructive' : 'outline'}>{refund.status}</Badge>
                        <span>{formatMoney(refund.amountMinor, payment.currency)}</span>
                        <span className="text-muted-foreground">{refund.reason}</span>
                        {refund.failureReason && <span className="text-destructive">{refund.failureReason}</span>}
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              <section className="flex flex-col gap-1">
                <h3 className="text-sm font-medium">{t('adminOrders.sheet.timeline')}</h3>
                <ol className="flex flex-col gap-2 border-l pl-3 text-sm">
                  {payment.timeline.map((entry) => (
                    <li key={`${entry.at}-${entry.kind}-${entry.label}`}>
                      <div className="font-medium">
                        {entry.label}
                        {entry.unprocessed && <Badge variant="destructive" className="ml-2">{t('adminOrders.sheet.unprocessed')}</Badge>}
                      </div>
                      <div className="text-muted-foreground text-xs">
                        {t(`adminOrders.sheet.kind.${entry.kind}`)} · {stamp(entry.at)}
                      </div>
                    </li>
                  ))}
                </ol>
              </section>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
