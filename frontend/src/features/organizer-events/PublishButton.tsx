import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { usePublishOrganizerEvent } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import type { OrganizerEventDetail } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError, problemList } from '@/lib/api-client'
import { formatDate, formatTime } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'
import { formatMoney } from '@/lib/money'

import { invalidateOrganizerEvent } from './eventRequest'
import type { ChecklistEntry } from './publishChecklist'
import { useEventSeatMap } from './useEventSeatMap'

type Step = 'closed' | 'confirm' | 'blocked'

/**
 * Nút Publish (màn #24). Thiếu mục nào thì hiện "Chưa thể publish" kèm danh sách ngay, không gọi server. Server vẫn
 * kiểm lại và trả lý do (BR-05: organizer chưa được duyệt, chưa nhận được tiền): hiện cùng một hộp thoại.
 */
export function PublishButton({ event, checklist }: { event: OrganizerEventDetail; checklist: ChecklistEntry[] }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [step, setStep] = useState<Step>('closed')
  const [blockedReasons, setBlockedReasons] = useState<string[]>([])
  const publish = usePublishOrganizerEvent()
  const seatMap = useEventSeatMap(event)

  const open = () => {
    const missing = checklist.filter((entry) => !entry.done).map((entry) => t(`organizerEvents.publish.missing.${entry.item}`))
    setBlockedReasons(missing)
    setStep(missing.length > 0 ? 'blocked' : 'confirm')
  }
  const close = () => {
    setStep('closed')
    publish.reset()
  }

  const confirm = () =>
    publish.mutate(
      { eventId: event.id },
      {
        onSuccess: () => {
          void invalidateOrganizerEvent(queryClient, event.id)
          toast.success(t('organizerEvents.publish.done'))
          close()
        },
        onError: (error) => {
          const code = error instanceof ApiError ? error.code : undefined
          if (code === 'PUBLISH_NOT_ALLOWED') {
            setBlockedReasons(problemList(error, 'reasons').map((reason) => t(`organizerEvents.publish.reasons.${reason}`, { defaultValue: reason })))
            setStep('blocked')
          } else if (code === 'PUBLISH_INCOMPLETE') {
            setBlockedReasons(problemList(error, 'missing').map((item) => t(`organizerEvents.publish.missing.${item}`, { defaultValue: item })))
            setStep('blocked')
          }
          // Lỗi khác (vd. INVALID_EVENT_TIMES) hiện trong hộp thoại xác nhận, bên dưới.
        },
      },
    )

  const sectionIds = new Set(event.tierSections.map((assignment) => assignment.sectionId))
  const seatCount = (seatMap?.sections ?? []).filter((section) => sectionIds.has(section.id)).reduce((sum, section) => sum + section.seatCount, 0)
  const prices = event.tiers.map((tier) => tier.priceMinor)
  const currency = event.tiers[0]?.currency ?? 'VND'

  return (
    <>
      <Button onClick={open}>{t('organizerEvents.publish.button')}</Button>

      <Dialog open={step === 'confirm'} onOpenChange={(next) => !next && close()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('organizerEvents.publish.confirmTitle', { title: event.title })}</DialogTitle>
            <DialogDescription>{t('organizerEvents.publish.confirmBody')}</DialogDescription>
          </DialogHeader>
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-muted-foreground">{t('organizerEvents.publish.saleStarts')}</dt>
            <dd>{event.saleStartsAt ? `${formatTime(event.saleStartsAt)}, ${formatDate(event.saleStartsAt)}` : '—'}</dd>
            <dt className="text-muted-foreground">{t('organizerEvents.publish.map')}</dt>
            <dd>{t('organizerEvents.publish.mapValue', { seats: seatCount, tiers: event.tiers.length })}</dd>
            <dt className="text-muted-foreground">{t('organizerEvents.publish.prices')}</dt>
            <dd>
              {prices.length > 0
                ? `${formatMoney(Math.min(...prices), currency)} – ${formatMoney(Math.max(...prices), currency)}`
                : '—'}
            </dd>
          </dl>
          <p className="text-muted-foreground text-sm">{t('organizerEvents.publish.lockNote')}</p>
          {publish.isError && (
            <p role="alert" className="text-destructive text-sm">
              {errorMessage(publish.error, t)}
            </p>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button disabled={publish.isPending} onClick={confirm}>
              {t('organizerEvents.publish.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={step === 'blocked'} onOpenChange={(next) => !next && close()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('organizerEvents.publish.blockedTitle')}</DialogTitle>
            <DialogDescription>{t('organizerEvents.publish.blockedBody')}</DialogDescription>
          </DialogHeader>
          <ul className="list-disc pl-5 text-sm">
            {blockedReasons.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ul>
          <DialogFooter>
            <Button onClick={close}>{t('organizerEvents.publish.understood')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
