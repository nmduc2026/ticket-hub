import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getListAdminAdministrativeUnitsQueryKey, useDisableAdministrativeUnit } from '@/api/generated/endpoints/admin-administrative-unit-controller/admin-administrative-unit-controller'
import type { AdminAdministrativeUnit } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { errorMessage } from '@/lib/error-messages'

/** Xác nhận tắt đơn vị hành chính; chặn khi còn con hoặc còn địa điểm (ADMIN_UNIT_IN_USE). */
export function DisableUnitDialog({ unit, onClose }: { unit: AdminAdministrativeUnit | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const disable = useDisableAdministrativeUnit()

  const close = () => {
    disable.reset()
    onClose()
  }

  const confirm = () => {
    if (!unit) return
    disable.mutate(
      { id: unit.id },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({ queryKey: getListAdminAdministrativeUnitsQueryKey() })
          toast.success(t('adminRegions.disabled'))
          close()
        },
      },
    )
  }

  return (
    <Dialog open={unit !== undefined} onOpenChange={(open) => !open && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('adminRegions.disableDialog.title', { name: unit?.name })}</DialogTitle>
          <DialogDescription>{t('adminRegions.disableDialog.description')}</DialogDescription>
        </DialogHeader>
        {disable.isError && (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(disable.error, t)}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button type="button" variant="destructive" disabled={disable.isPending} onClick={confirm}>
            {t('adminRegions.disableDialog.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
