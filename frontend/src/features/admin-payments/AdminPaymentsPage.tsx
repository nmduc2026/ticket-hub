import { useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  getListAdminStripeEventsQueryKey,
  useCheckAdminStripeWebhook,
  useGetAdminPaymentConfig,
  useListAdminStripeEvents,
  useReprocessAdminStripeEvent,
} from '@/api/generated/endpoints/admin-stripe-config-controller/admin-stripe-config-controller'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatTime } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

const CONNECT_STATES = ['none', 'incomplete', 'pending', 'active', 'restricted'] as const

/** Cấu hình thanh toán, chỉ đọc (màn #36): khoá Stripe đã che, endpoint webhook, số organizer theo trạng thái Connect, event lỗi. */
export function AdminPaymentsPage() {
  const { t } = useTranslation()
  const configQuery = useGetAdminPaymentConfig()
  const config = configQuery.data?.status === 200 ? configQuery.data.data : undefined

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold tracking-tight">{t('adminPayments.title')}</h1>
      <p className="text-muted-foreground text-sm">{t('adminPayments.readOnly')}</p>

      {configQuery.isPending && <Skeleton className="h-48 w-full" aria-busy="true" />}
      {config && (
        <>
          <section className="bg-card flex flex-col gap-3 rounded-xl border p-4">
            <div className="flex items-center justify-between">
              <h2 className="font-medium">{t('adminPayments.stripeSection')}</h2>
              <Badge variant={config.mode === 'live' ? 'destructive' : 'outline'}>{t(`adminPayments.mode.${config.mode}`, { defaultValue: config.mode })}</Badge>
            </div>
            <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[180px_1fr]">
              <Row label={t('adminPayments.secretKey')} value={<code>{config.secretKeyMasked}</code>} />
              <Row label={t('adminPayments.apiVersion')} value={config.apiVersion} />
              <Row label={t('adminPayments.webhookEndpoint')} value={<code>{config.webhookEndpoint}</code>} />
              <Row label={t('adminPayments.connectWebhookEndpoint')} value={<code>{config.connectWebhookEndpoint}</code>} />
              <Row label={t('adminPayments.successRate')} value={`${config.successRate24h.toLocaleString('vi-VN')}%`} />
            </dl>
            <WebhookCheck />
          </section>

          <section className="bg-card flex flex-col gap-3 rounded-xl border p-4">
            <h2 className="font-medium">{t('adminPayments.connectSection')}</h2>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-5">
              {CONNECT_STATES.map((state) => (
                <li key={state} className="rounded-lg border p-3">
                  <div className="text-2xl font-semibold">{(config.organizersByState[state] ?? 0).toLocaleString('vi-VN')}</div>
                  <div className="text-muted-foreground text-xs">{t(`adminPayments.state.${state}`)}</div>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}

      <FailedEvents />
    </div>
  )
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="break-all">{value}</dd>
    </>
  )
}

function WebhookCheck() {
  const { t } = useTranslation()
  const check = useCheckAdminStripeWebhook()
  const result = check.data?.status === 200 ? check.data.data : undefined

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Button size="sm" variant="outline" disabled={check.isPending} onClick={() => check.mutate()}>
        {t('adminPayments.checkWebhook')}
      </Button>
      {result && (
        <span role="status" className="text-sm">
          {result.ok ? t('adminPayments.checkOk', { code: result.statusCode }) : t('adminPayments.checkFailed', { code: result.statusCode, error: result.error ?? '' })}
        </span>
      )}
      {check.isError && (
        <span role="alert" className="text-destructive text-sm">
          {errorMessage(check.error, t)}
        </span>
      )}
    </div>
  )
}

function FailedEvents() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const query = useListAdminStripeEvents()
  const events = query.data?.status === 200 ? (query.data.data.items ?? []) : undefined
  const reprocess = useReprocessAdminStripeEvent({
    mutation: {
      onSuccess: (response) => {
        const processed = response.status === 200 && response.data.processed
        toast.success(t(processed ? 'adminPayments.reprocessed' : 'adminPayments.alreadyProcessed'))
        void queryClient.invalidateQueries({ queryKey: getListAdminStripeEventsQueryKey() })
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    },
  })

  return (
    <section className="flex flex-col gap-2">
      <h2 className="font-medium">{t('adminPayments.failedSection')}</h2>
      {query.isPending && <Skeleton className="h-32 w-full" aria-busy="true" />}
      {events && events.length === 0 && <p className="text-muted-foreground text-sm">{t('adminPayments.noFailed')}</p>}
      {events && events.length > 0 && (
        <div className="bg-card overflow-hidden rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('adminPayments.colEvent')}</TableHead>
                <TableHead>{t('adminPayments.colType')}</TableHead>
                <TableHead>{t('adminPayments.colTime')}</TableHead>
                <TableHead>{t('adminPayments.colPending')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {events.map((event) => (
                <TableRow key={event.id}>
                  <TableCell className="font-mono text-xs">{event.id}</TableCell>
                  <TableCell>{event.type}</TableCell>
                  <TableCell>{`${formatDate(event.createdAt)} ${formatTime(event.createdAt)}`}</TableCell>
                  <TableCell>{event.pendingWebhooks}</TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" variant="outline" disabled={reprocess.isPending} onClick={() => reprocess.mutate({ id: event.id })}>
                      {t('adminPayments.reprocess')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  )
}
