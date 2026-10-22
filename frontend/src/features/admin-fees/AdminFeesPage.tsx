import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { getListPlatformFeesQueryKey, useListPlatformFees, useSchedulePlatformFee } from '@/api/generated/endpoints/admin-platform-fee-controller/admin-platform-fee-controller'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field } from '@/components/form/Field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, vnInputsToIso } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

/** Phí nền tảng (màn #30, mức mặc định, BR-06): lịch sử các mức và đặt mức mới có ngày hiệu lực. */
export function AdminFeesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const query = useListPlatformFees()

  const fees = query.data?.status === 200 ? query.data.data : undefined

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight">{t('adminFees.title')}</h1>
        <Button size="sm" onClick={() => setOpen(true)}>
          {t('adminFees.schedule')}
        </Button>
      </div>

      {query.isPending && <Skeleton className="h-64 w-full" aria-busy="true" />}
      {fees && (
        <div className="bg-card overflow-hidden rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('adminFees.colFee')}</TableHead>
                <TableHead>{t('adminFees.colEffectiveFrom')}</TableHead>
                <TableHead>{t('adminFees.colCreatedBy')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {fees.map((fee) => (
                <TableRow key={fee.id}>
                  <TableCell className="font-medium">{(fee.feeBps / 100).toLocaleString('vi-VN')}%</TableCell>
                  <TableCell>{formatDate(fee.effectiveFrom)}</TableCell>
                  <TableCell className="text-muted-foreground">{fee.createdBy}</TableCell>
                  <TableCell>{fee.current && <Badge>{t('adminFees.current')}</Badge>}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <ScheduleFeeDialog
        open={open}
        onClose={() => setOpen(false)}
        onDone={() => void queryClient.invalidateQueries({ queryKey: getListPlatformFeesQueryKey() })}
      />
    </div>
  )
}

function ScheduleFeeDialog({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const schedule = useSchedulePlatformFee()
  const [percent, setPercent] = useState('')
  const [date, setDate] = useState('')
  const [touched, setTouched] = useState(false)

  const feeBps = Math.round(Number(percent) * 100)
  const percentInvalid = touched && (!Number.isFinite(feeBps) || feeBps < 0 || feeBps > 2000)
  const dateInvalid = touched && date.trim() === ''

  const close = () => {
    setPercent('')
    setDate('')
    setTouched(false)
    schedule.reset()
    onClose()
  }

  const submit = () => {
    const effectiveFrom = vnInputsToIso(date, '00:00')
    if (percentInvalid || dateInvalid || !effectiveFrom || !Number.isFinite(feeBps) || feeBps < 0 || feeBps > 2000) {
      setTouched(true)
      return
    }
    schedule.mutate({ data: { feeBps, effectiveFrom } }, { onSuccess: () => { close(); onDone() } })
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('adminFees.scheduleDialog.title')}</DialogTitle>
          <DialogDescription>{t('adminFees.scheduleDialog.description')}</DialogDescription>
        </DialogHeader>
        <Field id="fee-percent" label={t('adminFees.scheduleDialog.percent')} required error={percentInvalid ? t('adminFees.scheduleDialog.percentInvalid') : undefined}>
          <Input id="fee-percent" type="number" min={0} max={20} step={0.01} value={percent} aria-invalid={percentInvalid} onChange={(event) => setPercent(event.target.value)} />
        </Field>
        <Field id="fee-date" label={t('adminFees.scheduleDialog.effectiveFrom')} required error={dateInvalid ? t('adminFees.scheduleDialog.effectiveFromRequired') : undefined}>
          <Input id="fee-date" type="date" value={date} aria-invalid={dateInvalid} onChange={(event) => setDate(event.target.value)} />
        </Field>
        {schedule.isError && (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(schedule.error, t)}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button type="button" disabled={schedule.isPending} onClick={submit}>
            {t('adminFees.scheduleDialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
