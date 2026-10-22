import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { useUpdateOrganizerEvent } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import { useListVenueSeatMaps, useListVenues } from '@/api/generated/endpoints/venue-controller/venue-controller'
import type { OrganizerEventDetail } from '@/api/generated/model'
import {
  updateOrganizerEventBodyDescriptionMax,
  updateOrganizerEventBodyRulesMax,
  updateOrganizerEventBodyTitleMax,
} from '@/api/generated/zod/organizer-event-controller/organizer-event-controller.zod'
import { Field } from '@/components/form/Field'
import { Combobox } from '@/components/list/Combobox'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { EVENT_CATEGORIES } from '@/features/events/categories'
import { isoToVnInputs, vnInputsToIso } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'


import { BannerCard } from './BannerCard'
import { invalidateOrganizerEvent, toEventRequest } from './eventRequest'
import { useOrganizerEvent } from './useOrganizerEvent'

const required = 'organizerEvents.form.required'

/** Mốc độ tuổi hay dùng; giá trị khác đã lưu (nhập qua API) vẫn hiện trong danh sách. */
const AGE_OPTIONS = [13, 16, 18]

const formSchema = z.object({
  title: z.string().trim().min(1, required).max(updateOrganizerEventBodyTitleMax),
  category: z.string().min(1, required),
  description: z.string().trim().min(1, required).max(updateOrganizerEventBodyDescriptionMax),
  ageRestriction: z.string(),
  rules: z.string().max(updateOrganizerEventBodyRulesMax),
  date: z.string().min(1, required),
  startTime: z.string().min(1, required),
  endTime: z.string(),
  doorsTime: z.string(),
  venueId: z.string().min(1, required),
  seatMapId: z.string().min(1, required),
  highDemand: z.boolean(),
})
type FormValues = z.infer<typeof formSchema>

function toFormValues(event: OrganizerEventDetail): FormValues {
  const start = isoToVnInputs(event.startsAt)
  return {
    title: event.title,
    category: event.category ?? '',
    description: event.description ?? '',
    ageRestriction: event.ageRestriction == null ? '' : String(event.ageRestriction),
    rules: event.rules ?? '',
    date: start.date,
    startTime: start.time,
    endTime: isoToVnInputs(event.endsAt).time,
    doorsTime: isoToVnInputs(event.doorsOpenAt).time,
    venueId: event.venueId ?? '',
    seatMapId: event.seatMapId ?? '',
    highDemand: event.highDemand,
  }
}

/** Kết thúc ≤ bắt đầu trong cùng ngày nghĩa là qua nửa đêm: tính sang ngày hôm sau. */
function endInstant(startsAt: string | undefined, date: string, endTime: string): string | undefined {
  const end = vnInputsToIso(date, endTime)
  if (!startsAt || !end) return end
  return end <= startsAt ? new Date(new Date(end).getTime() + 24 * 3_600_000).toISOString() : end
}

/** Tab "Thông tin chung" (màn #24): ảnh bìa, thông tin cơ bản, thời gian và địa điểm. */
export function InfoTab({ eventId }: { eventId: string }) {
  const { event, isLoading } = useOrganizerEvent(eventId)
  if (isLoading) return <Skeleton className="h-96 max-w-3xl" aria-busy="true" />
  // Không có sự kiện (404 / lỗi): khung sửa sự kiện đã hiện trạng thái tương ứng, tab không vẽ gì thêm.
  if (!event) return null
  return (
    <div className="flex max-w-3xl flex-col gap-4">
      <BannerCard event={event} />
      <InfoForm event={event} />
    </div>
  )
}

function InfoForm({ event }: { event: OrganizerEventDetail }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const locked = event.salesInfoLocked
  const form = useForm<FormValues>({ resolver: zodResolver(formSchema), defaultValues: toFormValues(event) })
  const { errors, isDirty } = form.formState
  const venueId = form.watch('venueId')
  const [confirmSeatMap, setConfirmSeatMap] = useState<FormValues | null>(null)
  const update = useUpdateOrganizerEvent()

  const venues = useListVenues({ query: { staleTime: 5 * 60_000 } })
  const venueList = venues.data?.status === 200 ? venues.data.data : []
  const seatMaps = useListVenueSeatMaps(venueId, { query: { enabled: Boolean(venueId), staleTime: 5 * 60_000 } })
  const seatMapList = seatMaps.data?.status === 200 ? seatMaps.data.data : []

  const fieldError = (message: string | undefined) => (message ? t(message, { defaultValue: message }) : undefined)

  const ageOptions = [...new Set([...AGE_OPTIONS, ...(event.ageRestriction == null ? [] : [event.ageRestriction])])].sort((a, b) => a - b)

  const save = (values: FormValues) => {
    // Giờ mở cửa cùng ngày diễn; sau publish ô ngày bị khoá nên lấy ngày từ giờ diễn đã lưu.
    const showDate = locked ? isoToVnInputs(event.startsAt).date : values.date
    const sales = locked
      ? {}
      : (() => {
          const startsAt = vnInputsToIso(values.date, values.startTime)
          return {
            venueId: values.venueId,
            seatMapId: values.seatMapId,
            startsAt,
            endsAt: values.endTime ? endInstant(startsAt, values.date, values.endTime) : undefined,
            highDemand: values.highDemand,
          }
        })()
    update.mutate(
      {
        eventId: event.id,
        data: toEventRequest(event, {
          title: values.title.trim(),
          description: values.description.trim(),
          category: values.category,
          ageRestriction: values.ageRestriction ? Number(values.ageRestriction) : undefined,
          rules: values.rules.trim() || undefined,
          doorsOpenAt: values.doorsTime ? vnInputsToIso(showDate, values.doorsTime) : undefined,
          ...sales,
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
  }

  const submit = form.handleSubmit((values) => {
    // Đổi sơ đồ ghế khi đã gán hạng vé thì các gán đó mất (backend xoá): hỏi trước.
    if (!locked && event.tierSections.length > 0 && values.seatMapId !== (event.seatMapId ?? '')) {
      setConfirmSeatMap(values)
    } else {
      save(values)
    }
  })

  const hint = locked ? t('organizerEvents.form.lockedHint') : undefined

  return (
    <form onSubmit={(change) => void submit(change)} className="flex flex-col gap-4" noValidate>
      <section aria-labelledby="basic-heading" className="bg-card flex flex-col gap-4 rounded-xl border p-4">
        <h2 id="basic-heading" className="text-base font-semibold">
          {t('organizerEvents.form.basic')}
        </h2>
        <Field id="info-title" label={t('organizerEvents.form.title')} required error={fieldError(errors.title?.message)}>
          <Input id="info-title" aria-invalid={Boolean(errors.title)} aria-describedby="info-title-error" {...form.register('title')} />
        </Field>
        <Field id="info-category" label={t('organizerEvents.form.category')} required error={fieldError(errors.category?.message)}>
          <Controller
            control={form.control}
            name="category"
            render={({ field }) => (
              <Combobox
                id="info-category"
                value={field.value || undefined}
                onChange={field.onChange}
                options={EVENT_CATEGORIES.map((category) => ({ value: category, label: category }))}
                placeholder={t('organizerEvents.form.categoryPlaceholder')}
                aria-invalid={Boolean(errors.category)}
              />
            )}
          />
        </Field>
        <Field id="info-age" label={t('organizerEvents.form.ageRestriction')}>
          <Controller
            control={form.control}
            name="ageRestriction"
            render={({ field }) => (
              <Combobox
                id="info-age"
                value={field.value}
                onChange={field.onChange}
                placeholder={t('organizerEvents.form.ageAny')}
                options={[
                  { value: '', label: t('organizerEvents.form.ageAny') },
                  ...ageOptions.map((age) => ({ value: String(age), label: t('organizerEvents.form.agePlus', { age }) })),
                ]}
              />
            )}
          />
        </Field>
        <Field id="info-description" label={t('organizerEvents.form.description')} required error={fieldError(errors.description?.message)}>
          <Textarea id="info-description" rows={6} aria-invalid={Boolean(errors.description)} aria-describedby="info-description-error" {...form.register('description')} />
        </Field>
        <Field id="info-rules" label={t('organizerEvents.form.rules')} error={fieldError(errors.rules?.message)}>
          <Textarea id="info-rules" rows={3} aria-invalid={Boolean(errors.rules)} {...form.register('rules')} />
        </Field>
      </section>

      <section aria-labelledby="time-heading" className="bg-card flex flex-col gap-4 rounded-xl border p-4">
        <div className="flex flex-col gap-0.5">
          <h2 id="time-heading" className="text-base font-semibold">
            {t('organizerEvents.form.timeAndPlace')}
          </h2>
          <p className="text-muted-foreground text-sm">{locked ? t('organizerEvents.form.lockedHint') : t('organizerEvents.form.timeZone')}</p>
        </div>
        <div className="grid gap-3 sm:grid-cols-4">
          <Field id="info-date" label={t('organizerEvents.form.date')} required error={fieldError(errors.date?.message)}>
            <Input id="info-date" type="date" disabled={locked} aria-invalid={Boolean(errors.date)} {...form.register('date')} />
          </Field>
          <Field id="info-start" label={t('organizerEvents.form.startTime')} required error={fieldError(errors.startTime?.message)}>
            <Input id="info-start" type="time" disabled={locked} aria-invalid={Boolean(errors.startTime)} {...form.register('startTime')} />
          </Field>
          <Field id="info-end" label={t('organizerEvents.form.endTime')}>
            <Input id="info-end" type="time" disabled={locked} {...form.register('endTime')} />
          </Field>
          <Field id="info-doors" label={t('organizerEvents.form.doorsOpen')}>
            <Input id="info-doors" type="time" {...form.register('doorsTime')} />
          </Field>
        </div>
        <Field id="info-venue" label={t('organizerEvents.form.venue')} required hint={hint} error={fieldError(errors.venueId?.message)}>
          <Controller
            control={form.control}
            name="venueId"
            render={({ field }) => (
              <Combobox
                id="info-venue"
                value={field.value || undefined}
                disabled={locked}
                onChange={(next) => {
                  if (next !== field.value) form.setValue('seatMapId', '', { shouldDirty: true })
                  field.onChange(next)
                }}
                options={venueList.map((venue) => ({ value: venue.id, label: venue.name }))}
                placeholder={t('organizerEvents.form.venuePlaceholder')}
                aria-invalid={Boolean(errors.venueId)}
              />
            )}
          />
        </Field>
        <Field id="info-seatmap" label={t('organizerEvents.form.seatMap')} required hint={hint} error={fieldError(errors.seatMapId?.message)}>
          <Controller
            control={form.control}
            name="seatMapId"
            render={({ field }) => (
              <Combobox
                id="info-seatmap"
                value={field.value || undefined}
                disabled={locked || !venueId}
                onChange={field.onChange}
                options={seatMapList.map((map) => ({
                  value: map.id,
                  label: `${map.name} · ${map.sections.reduce((sum, section) => sum + section.seatCount, 0)} ${t('organizerEvents.form.seats')}`,
                }))}
                placeholder={t('organizerEvents.form.seatMapPlaceholder')}
                aria-invalid={Boolean(errors.seatMapId)}
              />
            )}
          />
        </Field>
      </section>

      <section aria-labelledby="queue-heading" className="bg-card flex flex-col gap-3 rounded-xl border p-4">
        <h2 id="queue-heading" className="text-base font-semibold">
          {t('organizerEvents.form.queue')}
        </h2>
        <Controller
          control={form.control}
          name="highDemand"
          render={({ field }) => (
            <div className="flex items-start gap-3">
              <Checkbox id="info-high-demand" checked={field.value} onCheckedChange={(value) => field.onChange(value === true)} disabled={locked} className="mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <Label htmlFor="info-high-demand">{t('organizerEvents.form.highDemand')}</Label>
                <p className="text-muted-foreground text-sm">{locked ? t('organizerEvents.form.lockedHint') : t('organizerEvents.form.highDemandHint')}</p>
              </div>
            </div>
          )}
        />
      </section>

      {update.isError && (
        <p role="alert" className="text-destructive text-sm">
          {errorMessage(update.error, t)}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" disabled={!isDirty || update.isPending} onClick={() => form.reset()}>
          {t('organizerEvents.form.discard')}
        </Button>
        <Button type="submit" disabled={!isDirty || update.isPending}>
          {t('organizerEvents.form.save')}
        </Button>
      </div>

      <Dialog open={confirmSeatMap !== null} onOpenChange={(open) => !open && setConfirmSeatMap(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('organizerEvents.form.changeSeatMapTitle')}</DialogTitle>
            <DialogDescription>{t('organizerEvents.form.changeSeatMapBody', { count: event.tierSections.length })}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setConfirmSeatMap(null)}>
              {t('organizerEvents.form.keepSeatMap')}
            </Button>
            <Button
              type="button"
              onClick={() => {
                if (confirmSeatMap) save(confirmSeatMap)
                setConfirmSeatMap(null)
              }}
            >
              {t('organizerEvents.form.changeSeatMap')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </form>
  )
}
