import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import { useApplyOrganizer } from '@/api/generated/endpoints/organizer-application-controller/organizer-application-controller'
import type { OrganizerApplication } from '@/api/generated/model'
import { Field } from '@/components/form/Field'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api-client'
import { errorMessage } from '@/lib/error-messages'

const required = 'organizerApply.form.required'

const formSchema = z.object({
  type: z.enum(['INDIVIDUAL', 'BUSINESS']),
  displayName: z.string().trim().min(1, required).max(120),
  legalName: z.string().trim().min(1, required).max(200),
  legalId: z.string().trim().min(1, required).max(30),
  contactEmail: z.string().trim().min(1, required).email('organizerApply.form.invalidEmail').max(200),
  contactPhone: z.string().trim().min(1, required).max(20),
  website: z.string().trim().max(300),
  introduction: z.string().trim().min(1, required).max(2000),
  eventScale: z.string().max(50),
})
type FormValues = z.infer<typeof formSchema>

/** Trường của form tương ứng với mã lỗi nghiệp vụ do server kiểm (mã số, số điện thoại, website). */
const SERVER_FIELD: Record<string, keyof FormValues> = {
  LEGAL_ID_INVALID: 'legalId',
  PHONE_INVALID: 'contactPhone',
  WEBSITE_INVALID: 'website',
}

interface Props {
  /** Điền sẵn khi nộp lại sau khi bị từ chối. */
  previous?: OrganizerApplication
  submitLabel: string
  onSubmitted: () => void
}

/** Form hồ sơ organizer (màn #22): cá nhân khai họ tên + CCCD, doanh nghiệp khai tên + mã số thuế. */
export function ApplicationForm({ previous, submitLabel, onSubmitted }: Props) {
  const { t } = useTranslation()
  const apply = useApplyOrganizer()
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      type: previous?.type ?? 'BUSINESS',
      displayName: previous?.displayName ?? '',
      legalName: previous?.legalName ?? '',
      legalId: previous?.legalId ?? '',
      contactEmail: previous?.contactEmail ?? '',
      contactPhone: previous?.contactPhone ?? '',
      website: previous?.website ?? '',
      introduction: previous?.introduction ?? '',
      eventScale: previous?.eventScale ?? '',
    },
  })
  const { errors } = form.formState
  const isBusiness = form.watch('type') === 'BUSINESS'
  const fieldError = (message: string | undefined) => (message ? t(message, { defaultValue: message }) : undefined)

  const submit = form.handleSubmit((values) => {
    apply.mutate(
      { data: { ...values, website: values.website || undefined, eventScale: values.eventScale || undefined } },
      {
        onSuccess: onSubmitted,
        onError: (error) => {
          const field = error instanceof ApiError && error.code ? SERVER_FIELD[error.code] : undefined
          if (field) form.setError(field, { message: errorMessage(error, t) })
        },
      },
    )
  })

  const showServerError =
    apply.isError && !(apply.error instanceof ApiError && apply.error.code && SERVER_FIELD[apply.error.code])

  return (
    <form onSubmit={(event) => void submit(event)} className="bg-card grid gap-4 rounded-xl border p-6 sm:grid-cols-2" noValidate>
      <div className="flex flex-col gap-1.5 sm:col-span-2">
        <Label id="apply-type-label">{t('organizerApply.form.type')}</Label>
        <Controller
          control={form.control}
          name="type"
          render={({ field }) => (
            <RadioGroup aria-labelledby="apply-type-label" value={field.value} onValueChange={field.onChange} className="flex gap-6">
              {(['BUSINESS', 'INDIVIDUAL'] as const).map((value) => (
                <Label key={value} className="flex items-center gap-2 font-normal">
                  <RadioGroupItem value={value} />
                  {t(value === 'BUSINESS' ? 'organizerApply.form.business' : 'organizerApply.form.individual')}
                </Label>
              ))}
            </RadioGroup>
          )}
        />
      </div>

      <div className="sm:col-span-2">
        <Field
          id="apply-displayName"
          label={t('organizerApply.form.displayName')}
          required
          hint={t('organizerApply.form.displayNameHint')}
          error={fieldError(errors.displayName?.message)}
        >
          <Input id="apply-displayName" aria-invalid={Boolean(errors.displayName)} aria-describedby="apply-displayName-error" {...form.register('displayName')} />
        </Field>
      </div>
      <Field
        id="apply-legalName"
        label={t(isBusiness ? 'organizerApply.form.legalNameBusiness' : 'organizerApply.form.legalNameIndividual')}
        required
        error={fieldError(errors.legalName?.message)}
      >
        <Input id="apply-legalName" aria-invalid={Boolean(errors.legalName)} aria-describedby="apply-legalName-error" {...form.register('legalName')} />
      </Field>
      <Field
        id="apply-legalId"
        label={t(isBusiness ? 'organizerApply.form.legalIdBusiness' : 'organizerApply.form.legalIdIndividual')}
        required
        hint={isBusiness ? undefined : t('organizerApply.form.legalIdIndividualHint')}
        error={fieldError(errors.legalId?.message)}
      >
        <Input id="apply-legalId" inputMode="numeric" aria-invalid={Boolean(errors.legalId)} aria-describedby="apply-legalId-error" {...form.register('legalId')} />
      </Field>
      <Field id="apply-contactEmail" label={t('organizerApply.form.contactEmail')} required error={fieldError(errors.contactEmail?.message)}>
        <Input id="apply-contactEmail" type="email" aria-invalid={Boolean(errors.contactEmail)} aria-describedby="apply-contactEmail-error" {...form.register('contactEmail')} />
      </Field>
      <Field id="apply-contactPhone" label={t('organizerApply.form.contactPhone')} required error={fieldError(errors.contactPhone?.message)}>
        <Input id="apply-contactPhone" type="tel" aria-invalid={Boolean(errors.contactPhone)} aria-describedby="apply-contactPhone-error" {...form.register('contactPhone')} />
      </Field>
      <div className="sm:col-span-2">
        <Field id="apply-website" label={t('organizerApply.form.website')} error={fieldError(errors.website?.message)}>
          <Input id="apply-website" type="url" placeholder="https://" aria-invalid={Boolean(errors.website)} aria-describedby="apply-website-error" {...form.register('website')} />
        </Field>
      </div>
      <div className="sm:col-span-2">
        <Field id="apply-introduction" label={t('organizerApply.form.introduction')} required error={fieldError(errors.introduction?.message)}>
          <Textarea
            id="apply-introduction"
            rows={4}
            placeholder={t('organizerApply.form.introductionPlaceholder')}
            aria-invalid={Boolean(errors.introduction)}
            aria-describedby="apply-introduction-error"
            {...form.register('introduction')}
          />
        </Field>
      </div>
      <div className="sm:col-span-2">
        <Field id="apply-eventScale" label={t('organizerApply.form.eventScale')}>
          <Controller
            control={form.control}
            name="eventScale"
            render={({ field }) => (
              <Select value={field.value || undefined} onValueChange={field.onChange}>
                <SelectTrigger id="apply-eventScale" className="w-full max-w-72">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(t('organizerApply.form.eventScaleOptions', { returnObjects: true }) as string[]).map((option) => (
                    <SelectItem key={option} value={option}>
                      {option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </Field>
      </div>

      {showServerError && (
        <p role="alert" className="text-destructive text-sm sm:col-span-2">
          {errorMessage(apply.error, t)}
        </p>
      )}
      <div className="sm:col-span-2">
        <Button type="submit" disabled={apply.isPending}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
