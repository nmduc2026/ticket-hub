import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { useListAdministrativeUnits } from '@/api/generated/endpoints/administrative-unit-controller/administrative-unit-controller'
import { getListAdminVenuesQueryKey, useCreateVenue, useUpdateVenue } from '@/api/generated/endpoints/admin-venue-controller/admin-venue-controller'
import type { AdminVenue } from '@/api/generated/model'
import { Combobox } from '@/components/list/Combobox'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { errorMessage } from '@/lib/error-messages'

const formSchema = z.object({
  name: z.string().min(1),
  address: z.string().min(1),
  wardId: z.string().min(1),
  capacity: z.coerce.number<string>().int().min(1),
})
type FormValues = z.input<typeof formSchema>

function defaults(venue: AdminVenue | undefined): FormValues {
  return { name: venue?.name ?? '', address: venue?.address ?? '', wardId: venue?.wardId ?? '', capacity: venue ? String(venue.capacity) : '' }
}

/** Thêm / sửa địa điểm (màn #16). */
export function VenueDialog({ open, venue, onClose }: { open: boolean; venue: AdminVenue | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const create = useCreateVenue()
  const update = useUpdateVenue()
  const wards = useListAdministrativeUnits({ level: 2 }, { query: { staleTime: 5 * 60_000 } })
  const wardOptions = (wards.data?.status === 200 ? wards.data.data : []).map((unit) => ({ value: unit.id, label: unit.name }))

  const form = useForm<FormValues, unknown, z.output<typeof formSchema>>({ resolver: zodResolver(formSchema), defaultValues: defaults(venue) })
  useEffect(() => {
    if (open) form.reset(defaults(venue))
  }, [open, venue, form])

  const pending = create.isPending || update.isPending
  const close = () => {
    create.reset()
    update.reset()
    onClose()
  }

  const submit = form.handleSubmit((values) => {
    const data = { name: values.name, address: values.address, wardId: values.wardId, capacity: values.capacity }
    const options = {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getListAdminVenuesQueryKey() })
        toast.success(t('adminVenues.saved'))
        close()
      },
    }
    if (venue) update.mutate({ id: venue.id, data }, options)
    else create.mutate({ data }, options)
  })

  const error = create.error ?? update.error

  return (
    <Dialog open={open} onOpenChange={(next) => !next && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{venue ? t('adminVenues.editTitle') : t('adminVenues.createTitle')}</DialogTitle>
          <DialogDescription>{t('adminVenues.dialogDescription')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-3" noValidate>
          <Field id="venue-name" label={t('adminVenues.colName')} required error={form.formState.errors.name ? t('organizerEvents.form.required') : undefined}>
            <Input id="venue-name" {...form.register('name')} />
          </Field>
          <Field id="venue-address" label={t('adminVenues.colAddress')} required error={form.formState.errors.address ? t('organizerEvents.form.required') : undefined}>
            <Input id="venue-address" {...form.register('address')} />
          </Field>
          <Field id="venue-ward" label={t('adminVenues.colWard')} required error={form.formState.errors.wardId ? t('organizerEvents.form.required') : undefined}>
            <Controller
              control={form.control}
              name="wardId"
              render={({ field }) => (
                <Combobox id="venue-ward" value={field.value} onChange={field.onChange} options={wardOptions} placeholder={t('adminVenues.wardPlaceholder')} />
              )}
            />
          </Field>
          <Field id="venue-capacity" label={t('adminVenues.colCapacity')} required error={form.formState.errors.capacity ? t('organizerEvents.form.required') : undefined}>
            <Input id="venue-capacity" type="number" min={1} {...form.register('capacity')} />
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
              {t('adminVenues.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
