import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useBulkSuspendEvents, useSuspendEvent } from '@/api/generated/endpoints/admin-event-controller/admin-event-controller'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/error-messages'

interface Props {
  /** `undefined` khi đóng; một id (dòng) hoặc nhiều id (hàng loạt). */
  target: { ids: string[]; title?: string } | undefined
  onClose: () => void
  onDone: () => void
}

/** Tạm dừng bán một hoặc nhiều sự kiện, lý do bắt buộc (BR-13, màn #34). */
export function SuspendEventDialog({ target, onClose, onDone }: Props) {
  const { t } = useTranslation()
  const suspendOne = useSuspendEvent()
  const bulkSuspend = useBulkSuspendEvents()
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const invalid = touched && reason.trim() === ''
  const pending = suspendOne.isPending || bulkSuspend.isPending
  const bulk = (target?.ids.length ?? 0) > 1

  const close = () => {
    setReason('')
    setTouched(false)
    suspendOne.reset()
    bulkSuspend.reset()
    onClose()
  }

  const submit = () => {
    if (!target) return
    if (reason.trim() === '') {
      setTouched(true)
      return
    }
    if (bulk) {
      bulkSuspend.mutate({ data: { ids: target.ids, reason: reason.trim() } }, { onSuccess: () => { close(); onDone() } })
    }
    else {
      suspendOne.mutate({ id: target.ids[0], data: { reason: reason.trim() } }, { onSuccess: () => { close(); onDone() } })
    }
  }

  const error = suspendOne.error ?? bulkSuspend.error

  return (
    <Dialog open={target !== undefined} onOpenChange={(open) => !open && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{bulk ? t('adminEvents.suspendDialog.titleBulk', { count: target?.ids.length }) : t('adminEvents.suspendDialog.title', { title: target?.title })}</DialogTitle>
          <DialogDescription>{t('adminEvents.suspendDialog.description')}</DialogDescription>
        </DialogHeader>
        <Field id="suspend-reason" label={t('adminEvents.suspendDialog.reason')} required error={invalid ? t('adminEvents.suspendDialog.reasonRequired') : undefined}>
          <Textarea id="suspend-reason" rows={3} maxLength={500} value={reason} aria-invalid={invalid} onChange={(event) => setReason(event.target.value)} />
        </Field>
        {error ? (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(error, t)}
          </p>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button type="button" variant="destructive" disabled={pending} onClick={submit}>
            {t('adminEvents.suspendDialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
