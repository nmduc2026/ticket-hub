import { useTranslation } from 'react-i18next'

import { useGetAdminUser } from '@/api/generated/endpoints/admin-user-controller/admin-user-controller'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatTime } from '@/lib/date'
import { formatMoney } from '@/lib/money'

/** Side sheet chi tiết người dùng (màn #28): hồ sơ, đơn hàng, vé, hoàn tiền và (với organizer) sự kiện. */
export function UserSheet({ userId, onClose }: { userId: string | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const query = useGetAdminUser(userId ?? '', { query: { enabled: userId !== undefined, retry: false } })
  const detail = query.data?.status === 200 ? query.data.data : undefined
  const name = detail?.profile.fullName ?? detail?.profile.email ?? ''
  const when = (iso: string) => `${formatDate(iso)} · ${formatTime(iso)}`

  return (
    <Sheet open={userId !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{t('adminUsers.sheet.title', { name })}</SheetTitle>
          <SheetDescription>{detail?.profile.email ?? ''}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-5 px-4 pb-4">
          {query.isPending && userId !== undefined && <Skeleton className="h-40 w-full" aria-busy="true" />}
          {query.isError && (
            <div role="alert" className="flex flex-col items-start gap-2 text-sm">
              {t('adminUsers.sheet.loadError')}
              <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
                {t('common.retry')}
              </Button>
            </div>
          )}
          {detail && (
            <>
              <Section title={t('adminUsers.sheet.bookings')} empty={detail.bookings.length === 0}>
                {detail.bookings.map((booking) => (
                  <li key={booking.bookingId} className="flex justify-between gap-3">
                    <span className="min-w-0 truncate">{booking.eventTitle}</span>
                    <span className="whitespace-nowrap">{formatMoney(booking.totalMinor, booking.currency)}</span>
                  </li>
                ))}
              </Section>
              <Section title={t('adminUsers.sheet.tickets')} empty={detail.tickets.length === 0}>
                {detail.tickets.map((ticket) => (
                  <li key={ticket.ticketId} className="flex justify-between gap-3">
                    <span className="min-w-0 truncate">{ticket.eventTitle}</span>
                    <span className="whitespace-nowrap">{ticket.seat}</span>
                  </li>
                ))}
              </Section>
              <Section title={t('adminUsers.sheet.refunds')} empty={detail.refunds.length === 0}>
                {detail.refunds.map((refund) => (
                  <li key={refund.bookingId} className="flex justify-between gap-3">
                    <span className="min-w-0 truncate">{refund.eventTitle}</span>
                    <span className="whitespace-nowrap">
                      {refund.amountMinor != null ? formatMoney(refund.amountMinor, refund.currency) : '—'}
                    </span>
                  </li>
                ))}
              </Section>
              {detail.events && (
                <Section title={t('adminUsers.sheet.events')} empty={detail.events.length === 0}>
                  {detail.events.map((event) => (
                    <li key={event.eventId} className="flex justify-between gap-3">
                      <span className="min-w-0 truncate">{event.title}</span>
                      <span className="whitespace-nowrap">{event.startsAt ? when(event.startsAt) : '—'}</span>
                    </li>
                  ))}
                </Section>
              )}
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

function Section({ title, empty, children }: { title: string; empty: boolean; children: React.ReactNode }) {
  const { t } = useTranslation()
  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-sm font-semibold">{title}</h3>
      {empty ? <p className="text-muted-foreground text-sm">{t('adminUsers.sheet.none')}</p> : <ul className="flex flex-col gap-1.5 text-sm">{children}</ul>}
    </section>
  )
}
