import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useRejectOrganizer } from '@/api/generated/endpoints/admin-organizer-controller/admin-organizer-controller'
import type { AdminOrganizer } from '@/api/generated/model'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/error-messages'

interface Props {
  organizer: AdminOrganizer | undefined
  onClose: () => void
  onRejected: () => void
}

/** Từ chối hồ sơ: lý do bắt buộc vì organizer đọc lý do này trong thông báo và email. */
export function RejectDialog({ organizer, onClose, onRejected }: Props) {
  const { t } = useTranslation()
  const reject = useRejectOrganizer()
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const invalid = touched && reason.trim() === ''

  const close = () => {
    setReason('')
    setTouched(false)
    reject.reset()
    onClose()
  }

  const submit = () => {
    if (!organizer) return
    if (reason.trim() === '') {
      setTouched(true)
      return
    }
    reject.mutate(
      { id: organizer.id, data: { reason: reason.trim() } },
      {
        onSuccess: () => {
          close()
          onRejected()
        },
      },
    )
  }

  return (
    <Dialog open={organizer !== undefined} onOpenChange={(open) => !open && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('adminOrganizers.rejectDialog.title', { name: organizer?.displayName })}</DialogTitle>
          <DialogDescription>{t('adminOrganizers.rejectDialog.description')}</DialogDescription>
        </DialogHeader>
        <Field id="reject-reason" label={t('adminOrganizers.rejectDialog.reason')} required error={invalid ? t('adminOrganizers.rejectDialog.reasonRequired') : undefined}>
          <Textarea
            id="reject-reason"
            rows={4}
            value={reason}
            maxLength={1000}
            aria-invalid={invalid}
            aria-describedby="reject-reason-error"
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
        {reject.isError && (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(reject.error, t)}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button type="button" variant="destructive" disabled={reject.isPending} onClick={submit}>
            {t('adminOrganizers.rejectDialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
