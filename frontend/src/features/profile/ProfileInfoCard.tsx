import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { ExternalLink } from 'lucide-react'
import { useRef, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { useListAdministrativeUnits } from '@/api/generated/endpoints/administrative-unit-controller/administrative-unit-controller'
import {
  getGetMyProfileQueryKey,
  useUpdateMyProfile,
  useUploadMyAvatar,
} from '@/api/generated/endpoints/my-profile-controller/my-profile-controller'
import type { Profile } from '@/api/generated/model'
import { Field } from '@/components/form/Field'
import { Combobox } from '@/components/list/Combobox'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { KEYCLOAK_ACCOUNT_URL } from '@/lib/config'
import { errorMessage } from '@/lib/error-messages'
import { ACCEPTED_IMAGE_TYPES, imageFileError } from '@/lib/image-upload'
import { initials } from '@/lib/initials'

const formSchema = z.object({
  phone: z.string().trim().max(20),
  dateOfBirth: z.string(),
  provinceId: z.string(),
})
type FormValues = z.infer<typeof formSchema>

function toFormValues(profile: Profile): FormValues {
  return { phone: profile.phone ?? '', dateOfBirth: profile.dateOfBirth ?? '', provinceId: profile.province?.id ?? '' }
}

/** Thông tin cá nhân (#19): ảnh đại diện, SĐT, ngày sinh, tỉnh / thành. Họ tên và email do Keycloak giữ, chỉ đọc. */
export function ProfileInfoCard({ profile }: { profile: Profile }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const form = useForm<FormValues>({ resolver: zodResolver(formSchema), defaultValues: toFormValues(profile) })
  const update = useUpdateMyProfile()
  const provinces = useListAdministrativeUnits({ level: 1 }, { query: { staleTime: 60 * 60_000 } })
  const provinceList = provinces.data?.status === 200 ? provinces.data.data : []

  const save = form.handleSubmit((values) => {
    update.mutate(
      {
        data: {
          phone: values.phone || undefined,
          dateOfBirth: values.dateOfBirth || undefined,
          provinceId: values.provinceId || undefined,
        },
      },
      {
        onSuccess: (response) => {
          if (response.status !== 200) return
          queryClient.setQueryData(getGetMyProfileQueryKey(), response)
          form.reset(toFormValues(response.data))
          toast.success(t('profile.info.saved'))
        },
      },
    )
  })

  return (
    <form onSubmit={(submit) => void save(submit)} className="flex flex-col gap-5" noValidate>
      <h2 className="text-base font-semibold">{t('profile.info.heading')}</h2>
      <AvatarPicker profile={profile} />
      <dl className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <dt className="text-muted-foreground text-sm">{t('profile.info.fullName')}</dt>
          <dd className="text-sm font-medium break-words">{profile.fullName ?? '—'}</dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-muted-foreground text-sm">{t('profile.info.email')}</dt>
          <dd className="flex flex-wrap items-center gap-2 text-sm font-medium break-words">
            {profile.email ?? '—'}
            <a href={KEYCLOAK_ACCOUNT_URL} target="_blank" rel="noreferrer" className="text-primary inline-flex items-center gap-1 text-xs">
              {t('profile.info.changeEmail')}
              <ExternalLink className="size-3" aria-hidden="true" />
            </a>
          </dd>
        </div>
      </dl>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field id="profile-phone" label={t('profile.info.phone')}>
          <Input id="profile-phone" type="tel" inputMode="tel" autoComplete="tel" {...form.register('phone')} />
        </Field>
        <Field id="profile-dob" label={t('profile.info.dateOfBirth')}>
          <Input id="profile-dob" type="date" {...form.register('dateOfBirth')} />
        </Field>
        <Field id="profile-province" label={t('profile.info.province')}>
          <Controller
            control={form.control}
            name="provinceId"
            render={({ field }) => (
              <Combobox
                id="profile-province"
                value={field.value || undefined}
                onChange={field.onChange}
                options={provinceList.map((unit) => ({ value: unit.id, label: unit.name }))}
                placeholder={t('profile.info.provincePlaceholder')}
              />
            )}
          />
        </Field>
      </div>
      {update.isError && (
        <p role="alert" className="text-destructive text-sm">
          {errorMessage(update.error, t)}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" disabled={!form.formState.isDirty || update.isPending} onClick={() => form.reset()}>
          {t('profile.info.discard')}
        </Button>
        <Button type="submit" disabled={!form.formState.isDirty || update.isPending}>
          {t('profile.info.save')}
        </Button>
      </div>
    </form>
  )
}

/** Chọn ảnh là tải lên ngay (`POST /me/avatar`). */
function AvatarPicker({ profile }: { profile: Profile }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [localError, setLocalError] = useState<string | null>(null)
  const upload = useUploadMyAvatar()

  const onFile = (file: File | undefined) => {
    setLocalError(null)
    if (!file) return
    const fileError = imageFileError(file)
    if (fileError) return setLocalError(t(`errors.${fileError}`))
    upload.mutate(
      { data: { file } },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({ queryKey: getGetMyProfileQueryKey() })
          toast.success(t('profile.info.avatarUploaded'))
        },
      },
    )
    if (input.current) input.current.value = ''
  }

  const error = localError ?? (upload.error ? errorMessage(upload.error, t) : null)
  return (
    <div className="flex flex-wrap items-center gap-4">
      <Avatar size="lg">
        {profile.avatarUrl && <AvatarImage src={profile.avatarUrl} alt={t('profile.info.avatar')} />}
        <AvatarFallback className="bg-primary/10 text-primary font-semibold">{initials(profile.fullName, profile.email)}</AvatarFallback>
      </Avatar>
      <div className="flex flex-col gap-1">
        <input
          ref={input}
          id="avatar-file"
          type="file"
          accept={ACCEPTED_IMAGE_TYPES.join(',')}
          className="sr-only"
          aria-label={t('profile.info.uploadAvatar')}
          onChange={(change) => onFile(change.target.files?.[0])}
        />
        <Button type="button" variant="outline" size="sm" disabled={upload.isPending} onClick={() => input.current?.click()}>
          {t('profile.info.uploadAvatar')}
        </Button>
        <p className="text-muted-foreground text-xs">{t('profile.info.avatarHint')}</p>
        {error && (
          <p role="alert" className="text-destructive text-xs">
            {error}
          </p>
        )}
      </div>
    </div>
  )
}
