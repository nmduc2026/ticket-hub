import { Link } from '@tanstack/react-router'
import { AlertCircle } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { useGetOrganizerDashboard } from '@/api/generated/endpoints/organizer-sales-controller/organizer-sales-controller'
import type { OrganizerBooking, OrganizerDashboard } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatTime } from '@/lib/date'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/utils'

const RANGES = [7, 30, 90] as const

/** Tổng quan organizer (màn #11): doanh thu theo ngày, đơn gần đây. Dữ liệu từ `GET /organizer/dashboard`. */
export function OrganizerDashboardPage() {
  const { t } = useTranslation()
  const [days, setDays] = useState<(typeof RANGES)[number]>(30)
  const query = useGetOrganizerDashboard({ days }, { query: { retry: false } })

  return (
    <section className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('organizer.title')}</h1>
          <p className="text-muted-foreground text-sm">{t('organizerDashboard.subtitle')}</p>
        </div>
        <div className="flex gap-1" role="group" aria-label={t('organizerDashboard.rangeLabel')}>
          {RANGES.map((range) => (
            <Button key={range} size="sm" variant={range === days ? 'default' : 'outline'} onClick={() => setDays(range)}>
              {t('organizerDashboard.rangeDays', { count: range })}
            </Button>
          ))}
        </div>
      </div>

      {query.isPending && <Skeleton className="h-96 w-full" aria-busy="true" />}
      {query.isError && (
        <StatusPanel icon={<AlertCircle />} title={t('organizerDashboard.loadError')}>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        </StatusPanel>
      )}
      {query.data?.status === 200 && <DashboardBody dashboard={query.data.data} />}
    </section>
  )
}

function DashboardBody({ dashboard }: { dashboard: OrganizerDashboard }) {
  const { t } = useTranslation()
  const stats = [
    { label: t('organizerDashboard.gross'), value: formatMoney(dashboard.grossMinor, dashboard.currency) },
    { label: t('organizerDashboard.fee'), value: formatMoney(dashboard.feeMinor, dashboard.currency) },
    { label: t('organizerDashboard.net'), value: formatMoney(dashboard.netMinor, dashboard.currency) },
    { label: t('organizerDashboard.ticketsSold'), value: String(dashboard.ticketsSold) },
  ]

  return (
    <div className="flex flex-col gap-5">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.label} className="bg-card flex flex-col gap-1 rounded-xl border p-4">
            <span className="text-muted-foreground text-sm">{stat.label}</span>
            <span className="text-2xl font-semibold tracking-tight">{stat.value}</span>
          </div>
        ))}
      </div>

      <section className="bg-card flex flex-col gap-3 rounded-xl border p-4">
        <h2 className="font-semibold">{t('organizerDashboard.byDay')}</h2>
        {dashboard.byDay.length === 0 ? (
          <p className="text-muted-foreground py-8 text-center text-sm">{t('organizerEventSales.noData')}</p>
        ) : (
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={dashboard.byDay} margin={{ top: 4, right: 4, left: 4, bottom: 0 }}>
                <CartesianGrid vertical={false} stroke="var(--color-border)" />
                <XAxis dataKey="date" tickFormatter={(value: string) => formatDate(value)} tick={{ fontSize: 12, fill: 'var(--color-muted-foreground)' }} axisLine={false} tickLine={false} />
                <YAxis tickFormatter={(value: number) => formatMoney(value, dashboard.currency)} tick={{ fontSize: 12, fill: 'var(--color-muted-foreground)' }} width={90} axisLine={false} tickLine={false} />
                <Tooltip
                  formatter={(value: number) => formatMoney(value, dashboard.currency)}
                  labelFormatter={(value: string) => formatDate(value)}
                  contentStyle={{ background: 'var(--color-popover)', border: '1px solid var(--color-border)', borderRadius: 8, fontSize: 12 }}
                />
                <Bar dataKey="grossMinor" name={t('organizerDashboard.gross')} fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={40} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </section>

      <section className="bg-card overflow-hidden rounded-xl border">
        <div className="flex items-center justify-between p-4">
          <h2 className="font-semibold">{t('organizerDashboard.recentBookings')}</h2>
          <Button variant="outline" size="sm" asChild>
            <Link to="/organizer/orders">{t('organizerDashboard.viewAll')}</Link>
          </Button>
        </div>
        {dashboard.recentBookings.length === 0 ? (
          <p className="text-muted-foreground px-4 pb-4 text-sm">{t('organizerEventSales.noData')}</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('organizerDashboard.colId')}</TableHead>
                  <TableHead>{t('organizerDashboard.colEvent')}</TableHead>
                  <TableHead>{t('organizerDashboard.colStatus')}</TableHead>
                  <TableHead>{t('organizerDashboard.colTime')}</TableHead>
                  <TableHead className="text-right">{t('organizerDashboard.colTotal')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {dashboard.recentBookings.map((booking: OrganizerBooking) => (
                  <TableRow key={booking.bookingId}>
                    <TableCell className={cn('font-mono text-xs whitespace-nowrap')}>{booking.bookingId.slice(-8).toUpperCase()}</TableCell>
                    <TableCell className="font-medium">{booking.eventTitle}</TableCell>
                    <TableCell className="text-muted-foreground">{booking.status}</TableCell>
                    <TableCell className="text-muted-foreground whitespace-nowrap">{`${formatDate(booking.createdAt)} · ${formatTime(booking.createdAt)}`}</TableCell>
                    <TableCell className="text-right whitespace-nowrap">{formatMoney(booking.totalMinor, booking.currency)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </section>
    </div>
  )
}
