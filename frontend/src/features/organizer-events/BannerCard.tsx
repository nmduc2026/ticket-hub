import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useUploadEventBanner } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import type { OrganizerEventDetail } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { EventPoster } from '@/features/events/EventPoster'
import { errorMessage } from '@/lib/error-messages'

import { invalidateOrganizerEvent } from './eventRequest'

const MAX_BYTES = 2 * 1024 * 1024
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp']

/**
 * Ảnh bìa (màn #24). Chọn ảnh là gửi ngay (`POST /organizer/events/{id}/banner`, multipart); kiểm loại và cỡ ở client
 * để báo sớm, backend vẫn kiểm lại theo nội dung file. Sửa được cả sau publish vì là thông tin hiển thị (BR-14).
 */
export function BannerCard({ event }: { event: OrganizerEventDetail }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [localError, setLocalError] = useState<string | null>(null)
  const upload = useUploadEventBanner()

  const onFile = (file: File | undefined) => {
    setLocalError(null)
    if (!file) return
    if (!ACCEPTED.includes(file.type)) return setLocalError(t('errors.UNSUPPORTED_IMAGE_TYPE'))
    if (file.size > MAX_BYTES) return setLocalError(t('errors.IMAGE_TOO_LARGE'))
    upload.mutate(
      { eventId: event.id, data: { file } },
      {
        onSuccess: () => {
          void invalidateOrganizerEvent(queryClient, event.id)
          toast.success(t('organizerEvents.banner.uploaded'))
        },
      },
    )
    // Cho phép chọn lại đúng file đó sau khi sửa lỗi.
    if (input.current) input.current.value = ''
  }

  const error = localError ?? (upload.error ? errorMessage(upload.error, t) : null)

  return (
    <section aria-labelledby="banner-heading" className="bg-card flex flex-col gap-3 rounded-xl border p-4">
      <div className="flex flex-col gap-0.5">
        <h2 id="banner-heading" className="text-base font-semibold">
          {t('organizerEvents.banner.title')}
        </h2>
        <p className="text-muted-foreground text-sm">{t('organizerEvents.banner.hint')}</p>
      </div>
      <div className="aspect-video max-w-md overflow-hidden rounded-lg border">
        <EventPoster title={event.title} bannerUrl={event.bannerUrl} />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <input
          ref={input}
          id="banner-file"
          type="file"
          accept={ACCEPTED.join(',')}
          className="sr-only"
          onChange={(change) => onFile(change.target.files?.[0])}
        />
        <Button type="button" variant="outline" size="sm" disabled={upload.isPending} onClick={() => input.current?.click()}>
          {t('organizerEvents.banner.upload')}
        </Button>
        {!event.bannerUrl && <span className="text-muted-foreground text-sm">{t('organizerEvents.banner.auto')}</span>}
      </div>
      {error && (
        <p role="alert" className="text-destructive text-sm">
          {error}
        </p>
      )}
    </section>
  )
}
