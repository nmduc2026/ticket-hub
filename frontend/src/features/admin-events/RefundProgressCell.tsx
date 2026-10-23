import { useTranslation } from 'react-i18next'

import { useGetEventRefundProgress } from '@/api/generated/endpoints/admin-event-refund-controller/admin-event-refund-controller'

/** Tiến độ hoàn tiền của sự kiện đã huỷ (BR-08, màn #34). */
export function RefundProgressCell({ eventId }: { eventId: string }) {
  const { t } = useTranslation()
  const query = useGetEventRefundProgress(eventId)
  const progress = query.data?.status === 200 ? query.data.data : undefined
  if (!progress) return <span className="text-muted-foreground">—</span>
  const pct = progress.total > 0 ? Math.round((progress.refunded / progress.total) * 100) : 100
  return (
    <div className="flex items-center gap-2">
      <div className="bg-muted h-1.5 w-20 overflow-hidden rounded-full">
        <div className="bg-primary h-full rounded-full" style={{ width: `${pct}%` }} />
      </div>
      <span className="text-muted-foreground text-xs whitespace-nowrap">
        {t('adminEvents.refundProgress', { refunded: progress.refunded, total: progress.total })}
        {progress.failed > 0 && <span className="text-destructive"> · {t('adminEvents.refundFailedCount', { count: progress.failed })}</span>}
      </span>
    </div>
  )
}
