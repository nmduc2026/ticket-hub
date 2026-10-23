import { Link } from '@tanstack/react-router'
import { AlertCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useGetAdminDashboard } from '@/api/generated/endpoints/admin-dashboard-controller/admin-dashboard-controller'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/date'
import { formatMoney } from '@/lib/money'

/** Tổng quan Admin (màn #35): doanh thu gộp, phí, vé bán, sự kiện đang bán, danh sách cần xử lý, sự kiện sắp mở bán. */
export function AdminDashboardPage() {
  const { t } = useTranslation()
  const query = useGetAdminDashboard({ range: 30 }, { query: { retry: false } })

  if (query.isPending) {
    return (
      <section className="flex flex-col gap-5">
        <h1 className="text-2xl font-semibold tracking-tight">{t('admin.title')}</h1>
        <Skeleton className="h-96 w-full" aria-busy="true" />
      </section>
    )
  }
  if (query.isError) {
    return (
      <section className="flex flex-col gap-5">
        <h1 className="text-2xl font-semibold tracking-tight">{t('admin.title')}</h1>
        <StatusPanel icon={<AlertCircle />} title={t('adminDashboard.loadError')}>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        </StatusPanel>
      </section>
    )
  }
  const dashboard = query.data.status === 200 ? query.data.data : undefined
  if (!dashboard) return null
  const todo = dashboard.todo ?? {}

  const stats = [
    { label: t('adminDashboard.gross'), value: formatMoney(dashboard.grossMinor ?? 0) },
    { label: t('adminDashboard.fee'), value: formatMoney(dashboard.feeMinor ?? 0) },
    { label: t('adminDashboard.ticketsSold'), value: String(dashboard.ticketsSold ?? 0) },
    { label: t('adminDashboard.eventsOnSale'), value: String(dashboard.eventsOnSale ?? 0) },
  ]

  const todoItems: { label: string; count: number; to: string; search?: Record<string, unknown> }[] = [
    { label: t('adminDashboard.todo.pendingOrganizers'), count: todo.pendingOrganizers ?? 0, to: '/admin/organizers', search: { status: 'PENDING' } },
    { label: t('adminDashboard.todo.reconcile'), count: todo.reconcile ?? 0, to: '/admin/orders', search: { needsReconcile: true } },
    { label: t('adminDashboard.todo.refundFailed'), count: todo.refundFailed ?? 0, to: '/admin/orders', search: { status: ['REFUND_FAILED'] } },
    { label: t('adminDashboard.todo.suspendedEvents'), count: todo.suspendedEvents ?? 0, to: '/admin/events', search: { status: 'SUSPENDED' } },
    { label: t('adminDashboard.todo.stripeRestricted'), count: todo.stripeRestricted ?? 0, to: '/admin/organizers' },
  ]

  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold tracking-tight">{t('admin.title')}</h1>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.label} className="bg-card flex flex-col gap-1 rounded-xl border p-4">
            <span className="text-muted-foreground text-sm">{stat.label}</span>
            <span className="text-2xl font-semibold tracking-tight">{stat.value}</span>
          </div>
        ))}
      </div>

      <section className="bg-card flex flex-col gap-2 rounded-xl border p-4">
        <h2 className="font-semibold">{t('adminDashboard.todoTitle')}</h2>
        <ul className="flex flex-col divide-y">
          {todoItems.map((item) => (
            <li key={item.label}>
              <Link to={item.to} search={item.search} className="hover:bg-muted flex items-center justify-between rounded-md px-2 py-2 text-sm">
                <span>{item.label}</span>
                <span className={item.count > 0 ? 'font-semibold' : 'text-muted-foreground'}>{item.count}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="bg-card flex flex-col gap-2 rounded-xl border p-4">
        <h2 className="font-semibold">{t('adminDashboard.openingSoon')}</h2>
        {(dashboard.openingSoon ?? []).length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('organizerEventSales.noData')}</p>
        ) : (
          <ul className="flex flex-col divide-y">
            {(dashboard.openingSoon ?? []).map((event) => (
              <li key={event.eventId} className="flex items-center justify-between px-2 py-2 text-sm">
                <span className="font-medium">{event.title}</span>
                <span className="text-muted-foreground">{event.saleStartsAt ? formatDate(event.saleStartsAt) : '—'}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  )
}
