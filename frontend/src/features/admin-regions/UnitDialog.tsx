import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { getListAdminAdministrativeUnitsQueryKey, useCreateAdministrativeUnit, useListAdminAdministrativeUnits, useUpdateAdministrativeUnit } from '@/api/generated/endpoints/admin-administrative-unit-controller/admin-administrative-unit-controller'
import { AdminAdministrativeUnitType, type AdminAdministrativeUnit } from '@/api/generated/model'
import { Combobox } from '@/components/list/Combobox'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Label } from '@/components/ui/label'
import { errorMessage } from '@/lib/error-messages'
import { isoToVnInputs, vnInputsToIso } from '@/lib/date'

const LEVEL1_TYPES = [AdminAdministrativeUnitType.TINH, AdminAdministrativeUnitType.THANH_PHO]
const LEVEL2_TYPES = [AdminAdministrativeUnitType.PHUONG, AdminAdministrativeUnitType.XA, AdminAdministrativeUnitType.DAC_KHU]

const formSchema = z.object({
  level: z.enum(['1', '2']),
  code: z.string().min(1),
  name: z.string().min(1),
  type: z.enum(Object.values(AdminAdministrativeUnitType) as [string, ...string[]]),
  parentId: z.string().optional(),
  effectiveFrom: z.string().min(1),
})
type FormValues = z.input<typeof formSchema>

function defaults(unit: AdminAdministrativeUnit | undefined): FormValues {
  if (!unit) {
    return { level: '1', code: '', name: '', type: AdminAdministrativeUnitType.TINH, parentId: undefined, effectiveFrom: isoToVnInputs(new Date().toISOString()).date }
  }
  return {
    level: unit.level === 1 ? '1' : '2',
    code: unit.code,
    name: unit.name,
    type: unit.type,
    parentId: unit.parentId,
    effectiveFrom: unit.effectiveFrom.slice(0, 10),
  }
}

/** Thêm / sửa đơn vị hành chính (màn #33). */
export function UnitDialog({ open, unit, onClose }: { open: boolean; unit: AdminAdministrativeUnit | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const create = useCreateAdministrativeUnit()
  const update = useUpdateAdministrativeUnit()
  const provinces = useListAdminAdministrativeUnits({ level: 1 }, { query: { staleTime: 5 * 60_000 } })
  const provinceOptions = (provinces.data?.status === 200 ? provinces.data.data : []).map((item) => ({ value: item.id, label: item.name }))

  const form = useForm<FormValues, unknown, z.output<typeof formSchema>>({ resolver: zodResolver(formSchema), defaultValues: defaults(unit) })
  useEffect(() => {
    if (open) form.reset(defaults(unit))
  }, [open, unit, form])

  const level = form.watch('level')
  const pending = create.isPending || update.isPending
  const close = () => {
    create.reset()
    update.reset()
    onClose()
  }

  const submit = form.handleSubmit((values) => {
    const effectiveFrom = vnInputsToIso(values.effectiveFrom, '00:00')
    if (!effectiveFrom) return
    const data = {
      code: values.code,
      name: values.name,
      type: values.type as AdminAdministrativeUnitType,
      parentId: values.level === '2' ? values.parentId : undefined,
      effectiveFrom,
    }
    const options = {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getListAdminAdministrativeUnitsQueryKey() })
        toast.success(t('adminRegions.saved'))
        close()
      },
    }
    if (unit) update.mutate({ id: unit.id, data }, options)
    else create.mutate({ data }, options)
  })

  const error = create.error ?? update.error

  return (
    <Dialog open={open} onOpenChange={(next) => !next && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{unit ? t('adminRegions.editTitle') : t('adminRegions.createTitle')}</DialogTitle>
          <DialogDescription>{t('adminRegions.dialogDescription')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-3" noValidate>
          {!unit && (
            <Field id="unit-level" label={t('adminRegions.level')} required>
              <Controller
                control={form.control}
                name="level"
                render={({ field }) => (
                  <RadioGroup value={field.value} onValueChange={field.onChange} className="flex gap-4">
                    <div className="flex items-center gap-2">
                      <RadioGroupItem value="1" id="level-1" />
                      <Label htmlFor="level-1">{t('adminRegions.level1')}</Label>
                    </div>
                    <div className="flex items-center gap-2">
                      <RadioGroupItem value="2" id="level-2" />
                      <Label htmlFor="level-2">{t('adminRegions.level2')}</Label>
                    </div>
                  </RadioGroup>
                )}
              />
            </Field>
          )}
          <Field id="unit-type" label={t('adminRegions.colType')} required>
            <Controller
              control={form.control}
              name="type"
              render={({ field }) => (
                <RadioGroup value={field.value} onValueChange={field.onChange} className="flex flex-wrap gap-4">
                  {(level === '1' ? LEVEL1_TYPES : LEVEL2_TYPES).map((value) => (
                    <div key={value} className="flex items-center gap-2">
                      <RadioGroupItem value={value} id={`type-${value}`} />
                      <Label htmlFor={`type-${value}`}>{t(`adminRegions.typeNames.${value}`)}</Label>
                    </div>
                  ))}
                </RadioGroup>
              )}
            />
          </Field>
          {level === '2' && (
            <Field id="unit-parent" label={t('adminRegions.colParent')} required>
              <Controller
                control={form.control}
                name="parentId"
                render={({ field }) => (
                  <Combobox id="unit-parent" value={field.value} onChange={field.onChange} options={provinceOptions} placeholder={t('adminRegions.parentPlaceholder')} />
                )}
              />
            </Field>
          )}
          <Field id="unit-code" label={t('adminRegions.colCode')} required error={form.formState.errors.code ? t('organizerEvents.form.required') : undefined}>
            <Input id="unit-code" {...form.register('code')} />
          </Field>
          <Field id="unit-name" label={t('adminRegions.colName')} required error={form.formState.errors.name ? t('organizerEvents.form.required') : undefined}>
            <Input id="unit-name" {...form.register('name')} />
          </Field>
          <Field id="unit-effective" label={t('adminRegions.colEffectiveFrom')} required error={form.formState.errors.effectiveFrom ? t('organizerEvents.form.required') : undefined}>
            <Input id="unit-effective" type="date" {...form.register('effectiveFrom')} />
          </Field>
          {error && (
            <p role="alert" className="text-destructive text-sm">
              {errorMessage(error, t)}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={pending}>
              {t('adminRegions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
