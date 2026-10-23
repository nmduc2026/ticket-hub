import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import {
  useCreateTicketTier,
  useReplaceTierSections,
  useUpdateTicketTier,
} from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import type { OrganizerEventDetail, SeatMapSection, TicketTier } from '@/api/generated/model'
import { createTicketTierBodyNameMax } from '@/api/generated/zod/organizer-event-controller/organizer-event-controller.zod'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { errorMessage } from '@/lib/error-messages'

import { invalidateOrganizerEvent } from './eventRequest'

const required = 'organizerEvents.form.required'

const formSchema = z.object({
  name: z.string().trim().min(1, required).max(createTicketTierBodyNameMax),
  // VND là zero-decimal: giá nhập chính là priceMinor, không nhân 100 (BR-10).
  price: z.coerce.number<string>().int('organizerEvents.tiers.priceInvalid').min(0, 'organizerEvents.tiers.priceInvalid'),
  sectionIds: z.array(z.string()),
})
type FormValues = z.input<typeof formSchema>

interface Props {
  event: OrganizerEventDetail
  /** Khu của sơ đồ ghế đang chọn cho sự kiện. */
  sections: SeatMapSection[]
  /** `undefined` là thêm hạng vé mới. */
  tier?: TicketTier
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Modal thêm / sửa hạng vé (màn #13). Lưu hạng vé rồi thay cách gán khu bằng `PUT tier-sections`: danh sách gửi lên là
 * trạng thái mới của cả sự kiện, nên giữ nguyên các khu đã gán cho hạng vé khác. Mỗi khu chỉ thuộc một hạng vé.
 */
export function TierDialog({ event, sections, tier, open, onOpenChange }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const createTier = useCreateTicketTier()
  const updateTier = useUpdateTicketTier()
  const replaceSections = useReplaceTierSections()
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const tierBySection = new Map(event.tierSections.map((assignment) => [assignment.sectionId, assignment.tierId]))
  const tierName = (tierId: string) => event.tiers.find((candidate) => candidate.id === tierId)?.name ?? ''
  const form = useForm<FormValues, unknown, z.output<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: tier?.name ?? '',
      price: tier ? String(tier.priceMinor) : '',
      sectionIds: tier ? event.tierSections.filter((a) => a.tierId === tier.id).map((a) => a.sectionId) : [],
    },
  })
  const { errors } = form.formState
  const fieldError = (message: string | undefined) => (message ? t(message, { defaultValue: message }) : undefined)

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    setSaving(true)
    try {
      const body = { name: values.name.trim(), priceMinor: values.price }
      const saved = tier
        ? await updateTier.mutateAsync({ eventId: event.id, tierId: tier.id, data: body })
        : await createTier.mutateAsync({ eventId: event.id, data: body })
      const tierId = saved.data.id
      const others = event.tierSections.filter((a) => a.tierId !== tierId)
      const assignments = [...others, ...values.sectionIds.map((sectionId) => ({ sectionId, tierId }))]
      // Chưa có sơ đồ ghế thì chưa có khu để gán: chỉ gọi khi thật sự có thay đổi cách gán.
      if (values.sectionIds.length > 0 || others.length !== event.tierSections.length) {
        await replaceSections.mutateAsync({ eventId: event.id, data: { assignments } })
      }
      onOpenChange(false)
    } catch (caught) {
      setError(errorMessage(caught, t))
    } finally {
      setSaving(false)
      // Hạng vé có thể đã lưu dù bước gán lỗi: luôn tải lại để màn hình khớp server.
      void invalidateOrganizerEvent(queryClient, event.id)
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{tier ? t('organizerEvents.tiers.editTitle') : t('organizerEvents.tiers.addTitle')}</DialogTitle>
          <DialogDescription>{t('organizerEvents.tiers.dialogHint')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={(change) => void submit(change)} className="flex flex-col gap-4" noValidate>
          <Field id="tier-name" label={t('organizerEvents.tiers.name')} required error={fieldError(errors.name?.message)}>
            <Input id="tier-name" aria-invalid={Boolean(errors.name)} aria-describedby="tier-name-error" {...form.register('name')} />
          </Field>
          <Field id="tier-price" label={t('organizerEvents.tiers.price')} required error={fieldError(errors.price?.message)}>
            <div className="flex items-center gap-2">
              <Input
                id="tier-price"
                type="number"
                inputMode="numeric"
                min={0}
                step={1000}
                className="max-w-48"
                aria-invalid={Boolean(errors.price)}
                aria-describedby="tier-price-error"
                {...form.register('price')}
              />
              <span aria-hidden="true">₫</span>
            </div>
          </Field>

          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('organizerEvents.tiers.sections')}</legend>
            {sections.length === 0 ? (
              <p className="text-muted-foreground text-sm">{t('organizerEvents.tiers.noSeatMap')}</p>
            ) : (
              <Controller
                control={form.control}
                name="sectionIds"
                render={({ field }) => (
                  <ul className="flex max-h-56 flex-col gap-1 overflow-y-auto">
                    {sections.map((section) => {
                      const owner = tierBySection.get(section.id)
                      const takenByOther = owner !== undefined && owner !== tier?.id
                      const id = `tier-section-${section.code}`
                      return (
                        <li key={section.id} className="hover:bg-muted flex items-center gap-2 rounded-md px-2 py-1.5">
                          <Checkbox
                            id={id}
                            disabled={takenByOther}
                            checked={field.value.includes(section.id)}
                            onCheckedChange={(checked) =>
                              field.onChange(
                                checked === true ? [...field.value, section.id] : field.value.filter((value) => value !== section.id),
                              )
                            }
                          />
                          <label htmlFor={id} className="flex flex-1 cursor-pointer items-baseline justify-between gap-3 text-sm">
                            <span>{section.name}</span>
                            <span className="text-muted-foreground text-xs">
                              {takenByOther
                                ? t('organizerEvents.tiers.assignedTo', { name: tierName(owner) })
                                : t('organizerEvents.tiers.seatCount', { count: section.seatCount })}
                            </span>
                          </label>
                        </li>
                      )
                    })}
                  </ul>
                )}
              />
            )}
          </fieldset>

          {error && (
            <p role="alert" className="text-destructive text-sm">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={saving}>
              {t('organizerEvents.tiers.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
