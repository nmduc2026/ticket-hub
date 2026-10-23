import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import { useCreateOrganizerEvent } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import { useListVenues } from '@/api/generated/endpoints/venue-controller/venue-controller'
import { createOrganizerEventBodyTitleMax } from '@/api/generated/zod/organizer-event-controller/organizer-event-controller.zod'
import { Field } from '@/components/form/Field'
import { Combobox } from '@/components/list/Combobox'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { EVENT_CATEGORIES } from '@/features/events/categories'
import { errorMessage } from '@/lib/error-messages'
import { vnInputsToIso } from '@/lib/date'

import { invalidateOrganizerEvent } from './eventRequest'

const required = 'organizerEvents.form.required'

const formSchema = z.object({
  title: z.string().trim().min(1, required).max(createOrganizerEventBodyTitleMax),
  category: z.string().min(1, required),
  venueId: z.string().min(1, required),
  date: z.string().min(1, required),
  time: z.string().min(1, required),
})
type FormValues = z.infer<typeof formSchema>

/** Modal "Tạo sự kiện mới" (màn #12): chỉ nhập phần tối thiểu, hạng vé, mô tả và ảnh bổ sung ở bước sau. */
export function CreateEventDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const venues = useListVenues({ query: { staleTime: 5 * 60_000, enabled: open } })
  const venueList = venues.data?.status === 200 ? venues.data.data : []
  const create = useCreateOrganizerEvent()

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { title: '', category: '', venueId: '', date: '', time: '' },
  })
  const { errors } = form.formState
  const fieldError = (message: string | undefined) => (message ? t(message, { defaultValue: message }) : undefined)

  const submit = form.handleSubmit((values) => {
    create.mutate(
      {
        data: {
          title: values.title.trim(),
          category: values.category,
          venueId: values.venueId,
          startsAt: vnInputsToIso(values.date, values.time),
        },
      },
      {
        onSuccess: (response) => {
          if (response.status !== 201) return
          void invalidateOrganizerEvent(queryClient, response.data.id)
          onOpenChange(false)
          form.reset()
          void navigate({ to: '/organizer/events/$eventId/edit', params: { eventId: response.data.id } })
        },
      },
    )
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          form.reset()
          create.reset()
        }
        onOpenChange(next)
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('organizerEvents.create.title')}</DialogTitle>
          <DialogDescription>{t('organizerEvents.create.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4" noValidate>
          <Field id="create-title" label={t('organizerEvents.form.title')} required error={fieldError(errors.title?.message)}>
            <Input id="create-title" aria-invalid={Boolean(errors.title)} aria-describedby="create-title-error" {...form.register('title')} />
          </Field>
          <Field id="create-category" label={t('organizerEvents.form.category')} required error={fieldError(errors.category?.message)}>
            <Controller
              control={form.control}
              name="category"
              render={({ field }) => (
                <Combobox
                  id="create-category"
                  value={field.value || undefined}
                  onChange={field.onChange}
                  options={EVENT_CATEGORIES.map((category) => ({ value: category, label: category }))}
                  placeholder={t('organizerEvents.form.categoryPlaceholder')}
                  aria-invalid={Boolean(errors.category)}
                />
              )}
            />
          </Field>
          <Field id="create-venue" label={t('organizerEvents.form.venue')} required error={fieldError(errors.venueId?.message)}>
            <Controller
              control={form.control}
              name="venueId"
              render={({ field }) => (
                <Combobox
                  id="create-venue"
                  value={field.value || undefined}
                  onChange={field.onChange}
                  options={venueList.map((venue) => ({ value: venue.id, label: venue.name }))}
                  placeholder={t('organizerEvents.form.venuePlaceholder')}
                  aria-invalid={Boolean(errors.venueId)}
                />
              )}
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field id="create-date" label={t('organizerEvents.form.date')} required error={fieldError(errors.date?.message)}>
              <Input id="create-date" type="date" aria-invalid={Boolean(errors.date)} {...form.register('date')} />
            </Field>
            <Field id="create-time" label={t('organizerEvents.form.startTime')} required error={fieldError(errors.time?.message)}>
              <Input id="create-time" type="time" aria-invalid={Boolean(errors.time)} {...form.register('time')} />
            </Field>
          </div>
          {create.isError && (
            <p role="alert" className="text-destructive text-sm">
              {errorMessage(create.error, t)}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {t('organizerEvents.create.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
