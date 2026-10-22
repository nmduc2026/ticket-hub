import { Link } from '@tanstack/react-router'
import { AlertCircle, PauseCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { useGetEventSales } from '@/api/generated/endpoints/organizer-sales-controller/organizer-sales-controller'
import type { TierSales } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/date'
import { formatMoney } from '@/lib/money'

import { useOrganizerEvent } from './useOrganizerEvent'

const TIER_COLORS = ['var(--color-chart-1)', 'var(--color-chart-2)', 'var(--color-chart-3)', 'var(--color-chart-4)', 'var(--color-chart-5)']

/** Bảng điều khiển sự kiện (màn #25, rút gọn theo plan Sprint 6 hạng mục 11): doanh thu theo ngày, lấp đầy theo hạng
 * vé, số check-in, banner khi bị tạm dừng bán. Dữ liệu từ `GET /organizer/events/{id}/sales`. */
export function EventDashboardTab({ eventId }: { eventId: string }) {
  const { t } = useTranslation()
  const { event } = useOrganizerEvent(eventId)
  const query = useGetEventSales(eventId, { query: { retry: false } })

  if (query.isPending) return <Skeleton className="h-96 w-full" aria-busy="true" />
  if (query.isError) {
    return (
      <StatusPanel icon={<AlertCircle />} title={t('organizerEventSales.loadError')}>
        <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
          {t('common.retry')}
        </Button>
      </StatusPanel>
    )
  }
  const sales = query.data.status === 200 ? query.data.data : undefined
  if (!sales) return null

  const stats = [
    { label: t('organizerEventSales.gross'), value: formatMoney(sales.grossMinor, sales.currency), sub: t('organizerEventSales.net', { amount: formatMoney(sales.netMinor, sales.currency) }) },
    { label: t('organizerEventSales.ticketsSold'), value: `${sales.ticketsSold} / ${sales.capacity}`, sub: sales.capacity > 0 ? `${Math.round((sales.ticketsSold / sales.capacity) * 100)}%` : '—' },
    { label: t('organizerEventSales.checkedIn'), value: String(sales.checkedIn), sub: sales.ticketsSold > 0 ? `${Math.round((sales.checkedIn / sales.ticketsSold) * 100)}%` : '—' },
    { label: t('organizerEventSales.fee'), value: formatMoney(sales.feeMinor, sales.currency), sub: sales.refundedMinor > 0 ? t('organizerEventSales.refunded', { amount: formatMoney(sales.refundedMinor, sales.currency) }) : undefined },
  ]

  return (
    <div className="flex flex-col gap-5">
      {event?.status === 'SUSPENDED' && (
        <div role="status" className="border-warning/40 bg-warning/10 flex items-center gap-2 rounded-xl border p-3 text-sm">
          <PauseCircle className="size-5 shrink-0" aria-hidden="true" />
          <span>{t('organizerEventSales.pausedBanner')}</span>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.label} className="bg-card flex flex-col gap-1 rounded-xl border p-4">
            <span className="text-muted-foreground text-sm">{stat.label}</span>
            <span className="text-2xl font-semibold tracking-tight">{stat.value}</span>
            {stat.sub && <span className="text-muted-foreground text-xs">{stat.sub}</span>}
          </div>
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-[3fr_2fr]">
        <section className="bg-card flex flex-col gap-3 rounded-xl border p-4">
          <h2 className="font-semibold">{t('organizerEventSales.byDay')}</h2>
          {sales.byDay.length === 0 ? (
            <p className="text-muted-foreground py-8 text-center text-sm">{t('organizerEventSales.noData')}</p>
          ) : (
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={sales.byDay} margin={{ top: 4, right: 4, left: 4, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="var(--color-border)" />
                  <XAxis dataKey="date" tickFormatter={(value: string) => formatDate(value)} tick={{ fontSize: 12, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} />
                  <YAxis tickFormatter={(value: number) => formatMoney(value, sales.currency)} tick={{ fontSize: 12, fill: 'var(--color-muted-foreground)' }} width={90} axisLine={false} tickLine={false} />
                  <Tooltip
                    formatter={(value: number) => formatMoney(value, sales.currency)}
                    labelFormatter={(value: string) => formatDate(value)}
                    contentStyle={{ background: 'var(--color-popover)', border: '1px solid var(--color-border)', borderRadius: 8, fontSize: 12 }}
                  />
                  <Bar dataKey="grossMinor" name={t('organizerEventSales.gross')} fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={40} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </section>

        <section className="bg-card flex flex-col gap-4 rounded-xl border p-4">
          <h2 className="font-semibold">{t('organizerEventSales.byTier')}</h2>
          {sales.byTier.length === 0 ? (
            <p className="text-muted-foreground text-sm">{t('organizerEventSales.noData')}</p>
          ) : (
            sales.byTier.map((tier: TierSales, index: number) => <TierBar key={tier.tierId} tier={tier} color={TIER_COLORS[index % TIER_COLORS.length]} currency={sales.currency} />)
          )}
        </section>
      </div>

      <div>
        <Button variant="outline" size="sm" asChild>
          <Link to="/organizer/orders" search={{ eventId }}>
            {t('organizerEventSales.viewOrders')}
          </Link>
        </Button>
      </div>
    </div>
  )
}

function TierBar({ tier, color, currency }: { tier: TierSales; color: string; currency: string }) {
  const { t } = useTranslation()
  const pct = tier.capacity > 0 ? Math.round((tier.sold / tier.capacity) * 100) : 0
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between text-sm">
        <span className="inline-flex items-center gap-2 font-medium">
          <span className="size-2.5 rounded-full" style={{ background: color }} aria-hidden="true" />
          {tier.tierName}
        </span>
        <span className="text-muted-foreground">
          {t('organizerEventSales.tierSold', { sold: tier.sold, capacity: tier.capacity })} · {formatMoney(tier.grossMinor, currency)}
        </span>
      </div>
      <div className="bg-muted h-2 overflow-hidden rounded-full">
        <div className="h-full rounded-full" style={{ width: `${pct}%`, background: color }} />
      </div>
    </div>
  )
}
