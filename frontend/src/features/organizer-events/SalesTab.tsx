import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { useUpdateOrganizerEvent } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import type { OrganizerEventDetail } from '@/api/generated/model'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { HOLD_MINUTES, MAX_SEATS_PER_BOOKING } from '@/lib/config'
import { isoToVnInputs, vnInputsToIso } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

import { invalidateOrganizerEvent, toEventRequest } from './eventRequest'
import { useOrganizerEvent } from './useOrganizerEvent'

const required = 'organizerEvents.form.required'

const formSchema = z.object({
  saleDate: z.string().min(1, required),
  saleTime: z.string().min(1, required),
  maxTicketsPerUser: z.coerce.number<string>().int().min(1, 'organizerEvents.sales.maxTicketsRange').max(100, 'organizerEvents.sales.maxTicketsRange'),
})
type FormValues = z.input<typeof formSchema>

function toFormValues(event: OrganizerEventDetail): FormValues {
  const sale = isoToVnInputs(event.saleStartsAt)
  return { saleDate: sale.date, saleTime: sale.time, maxTicketsPerUser: String(event.maxTicketsPerUser) }
}

/** Tab "Mở bán" (màn #24): giờ mở bán và giới hạn mua; khoá sau khi publish (BR-14). */
export function SalesTab({ eventId }: { eventId: string }) {
  const { event, isLoading } = useOrganizerEvent(eventId)
  if (isLoading) return <Skeleton className="h-72 max-w-3xl" aria-busy="true" />
  if (!event) return null
  return <SalesForm event={event} />
}

function SalesForm({ event }: { event: OrganizerEventDetail }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const locked = event.salesInfoLocked
  const form = useForm<FormValues, unknown, z.output<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: toFormValues(event),
  })
  const { errors, isDirty } = form.formState
  const update = useUpdateOrganizerEvent()
  const fieldError = (message: string | undefined) => (message ? t(message, { defaultValue: message }) : undefined)

  const submit = form.handleSubmit((values) => {
    update.mutate(
      {
        eventId: event.id,
        data: toEventRequest(event, {
          saleStartsAt: vnInputsToIso(values.saleDate, values.saleTime),
          maxTicketsPerUser: values.maxTicketsPerUser,
        }),
      },
      {
        onSuccess: (response) => {
          if (response.status !== 200) return
          void invalidateOrganizerEvent(queryClient, event.id)
          form.reset(toFormValues(response.data))
          toast.success(t('organizerEvents.form.saved'))
        },
      },
    )
  })

  const fee = event.platformFeeBps === undefined ? t('organizerEvents.sales.feePending') : t('organizerEvents.sales.fee', { percent: event.platformFeeBps / 100 })

  return (
    <form onSubmit={(change) => void submit(change)} className="flex max-w-3xl flex-col gap-4" noValidate>
      <section aria-labelledby="sale-time-heading" className="bg-card flex flex-col gap-4 rounded-xl border p-4">
        <div className="flex flex-col gap-0.5">
          <h2 id="sale-time-heading" className="text-base font-semibold">
            {t('organizerEvents.sales.time')}
          </h2>
          <p className="text-muted-foreground text-sm">{locked ? t('organizerEvents.form.lockedHint') : t('organizerEvents.form.timeZone')}</p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field id="sale-date" label={t('organizerEvents.sales.date')} required error={fieldError(errors.saleDate?.message)}>
            <Input id="sale-date" type="date" disabled={locked} aria-invalid={Boolean(errors.saleDate)} {...form.register('saleDate')} />
          </Field>
          <Field id="sale-time" label={t('organizerEvents.sales.hour')} required error={fieldError(errors.saleTime?.message)}>
            <Input id="sale-time" type="time" disabled={locked} aria-invalid={Boolean(errors.saleTime)} {...form.register('saleTime')} />
          </Field>
        </div>
      </section>

      <section aria-labelledby="limits-heading" className="bg-card flex flex-col gap-4 rounded-xl border p-4">
        <h2 id="limits-heading" className="text-base font-semibold">
          {t('organizerEvents.sales.limits')}
        </h2>
        <Field
          id="sale-max"
          label={t('organizerEvents.sales.maxTickets')}
          required
          hint={locked ? t('organizerEvents.form.lockedHint') : t('organizerEvents.sales.maxTicketsHint')}
          error={fieldError(errors.maxTicketsPerUser?.message)}
        >
          <Input
            id="sale-max"
            type="number"
            min={1}
            max={100}
            disabled={locked}
            className="max-w-32"
            aria-invalid={Boolean(errors.maxTicketsPerUser)}
            {...form.register('maxTicketsPerUser')}
          />
        </Field>
        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[auto_1fr]">
          <dt className="text-muted-foreground">{t('organizerEvents.sales.maxSeats')}</dt>
          <dd>{t('organizerEvents.sales.seatsValue', { count: MAX_SEATS_PER_BOOKING })}</dd>
          <dt className="text-muted-foreground">{t('organizerEvents.sales.hold')}</dt>
          <dd>{t('organizerEvents.sales.minutes', { count: HOLD_MINUTES })}</dd>
          <dt className="text-muted-foreground">{t('organizerEvents.sales.platformFee')}</dt>
          <dd>{fee}</dd>
        </dl>
      </section>

      {update.isError && (
        <p role="alert" className="text-destructive text-sm">
          {errorMessage(update.error, t)}
        </p>
      )}
      {!locked && (
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" disabled={!isDirty || update.isPending} onClick={() => form.reset()}>
            {t('organizerEvents.form.discard')}
          </Button>
          <Button type="submit" disabled={!isDirty || update.isPending}>
            {t('organizerEvents.form.save')}
          </Button>
        </div>
      )}
    </form>
  )
}
