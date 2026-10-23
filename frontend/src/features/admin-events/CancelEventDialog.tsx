import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useCancelEvent } from '@/api/generated/endpoints/admin-event-controller/admin-event-controller'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/error-messages'

const CONFIRM_WORD = 'HUY'

interface Props {
  target: { id: string; title: string } | undefined
  onClose: () => void
  onDone: () => void
}

/** Huỷ sự kiện (BR-08): hoàn 100% mọi đơn đã thanh toán, không hoàn lại được; gõ "HUY" để xác nhận hậu quả. */
export function CancelEventDialog({ target, onClose, onDone }: Props) {
  const { t } = useTranslation()
  const cancel = useCancelEvent()
  const [reason, setReason] = useState('')
  const [confirmWord, setConfirmWord] = useState('')
  const [touched, setTouched] = useState(false)
  const reasonInvalid = touched && reason.trim() === ''
  const wordInvalid = touched && confirmWord.trim().toUpperCase() !== CONFIRM_WORD

  const close = () => {
    setReason('')
    setConfirmWord('')
    setTouched(false)
    cancel.reset()
    onClose()
  }

  const submit = () => {
    if (!target) return
    if (reason.trim() === '' || confirmWord.trim().toUpperCase() !== CONFIRM_WORD) {
      setTouched(true)
      return
    }
    cancel.mutate({ id: target.id, data: { reason: reason.trim() } }, { onSuccess: () => { close(); onDone() } })
  }

  return (
    <Dialog open={target !== undefined} onOpenChange={(open) => !open && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('adminEvents.cancelDialog.title', { title: target?.title })}</DialogTitle>
          <DialogDescription>{t('adminEvents.cancelDialog.description')}</DialogDescription>
        </DialogHeader>
        <Field id="cancel-reason" label={t('adminEvents.cancelDialog.reason')} required error={reasonInvalid ? t('adminEvents.cancelDialog.reasonRequired') : undefined}>
          <Textarea id="cancel-reason" rows={3} maxLength={500} value={reason} aria-invalid={reasonInvalid} onChange={(event) => setReason(event.target.value)} />
        </Field>
        <Field
          id="cancel-confirm-word"
          label={t('adminEvents.cancelDialog.confirmWord', { word: CONFIRM_WORD })}
          required
          error={wordInvalid ? t('adminEvents.cancelDialog.confirmWordInvalid', { word: CONFIRM_WORD }) : undefined}
        >
          <Input id="cancel-confirm-word" value={confirmWord} aria-invalid={wordInvalid} onChange={(event) => setConfirmWord(event.target.value)} />
        </Field>
        {cancel.isError && (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(cancel.error, t)}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button type="button" variant="destructive" disabled={cancel.isPending} onClick={submit}>
            {t('adminEvents.cancelDialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
