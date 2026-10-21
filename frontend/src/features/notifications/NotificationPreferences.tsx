import { useQueryClient } from '@tanstack/react-query'
import { AlertCircle } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  getGetMyNotificationPreferencesQueryKey,
  useGetMyNotificationPreferences,
  useUpdateMyNotificationPreferences,
} from '@/api/generated/endpoints/my-notification-controller/my-notification-controller'
import type { NotificationPreference } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/error-messages'

type Draft = Record<string, { email: boolean; inApp: boolean }>

/** Cài đặt thông báo trong hồ sơ (màn #19): bật / tắt email và trong app theo loại. Loại bắt buộc luôn bật, không tắt được. */
export function NotificationPreferences() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const query = useGetMyNotificationPreferences({ query: { retry: false } })
  const [draft, setDraft] = useState<Draft>({})
  const save = useUpdateMyNotificationPreferences({
    mutation: {
      onSuccess: async () => {
        toast.success(t('notifications.preferences.saved'))
        setDraft({})
        await queryClient.invalidateQueries({ queryKey: getGetMyNotificationPreferencesQueryKey() })
      },
    },
  })

  if (query.isPending) return <Skeleton className="h-64 w-full" aria-busy="true" />
  if (query.isError) {
    return (
      <StatusPanel icon={<AlertCircle />} title={t('notifications.preferences.loadError')}>
        <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
          {t('common.retry')}
        </Button>
      </StatusPanel>
    )
  }

  const preferences: NotificationPreference[] = query.data.status === 200 ? query.data.data : []
  const current = (item: NotificationPreference) => draft[item.type] ?? { email: item.email, inApp: item.inApp }
  const set = (item: NotificationPreference, channel: 'email' | 'inApp', value: boolean) =>
    setDraft((previous) => ({ ...previous, [item.type]: { ...current(item), [channel]: value } }))
  const dirty = Object.keys(draft).length > 0

  const submit = () =>
    save.mutate({
      data: {
        preferences: preferences
          .filter((item) => !item.mandatory && draft[item.type])
          .map((item) => ({ type: item.type, ...current(item) })),
      },
    })

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold">{t('notifications.preferences.heading')}</h2>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-muted-foreground border-b text-left">
            <th scope="col" className="py-2 font-medium">
              {t('notifications.preferences.type')}
            </th>
            <th scope="col" className="w-20 py-2 text-center font-medium">
              {t('notifications.preferences.email')}
            </th>
            <th scope="col" className="w-20 py-2 text-center font-medium">
              {t('notifications.preferences.inApp')}
            </th>
          </tr>
        </thead>
        <tbody>
          {preferences.map((item) => {
            const value = current(item)
            return (
              <tr key={item.type} className="border-b last:border-0">
                <th scope="row" className="py-3 text-left font-medium">
                  <div>{item.label}</div>
                  {item.mandatory && <div className="text-muted-foreground text-xs font-normal">{t('notifications.preferences.mandatory')}</div>}
                </th>
                {(['email', 'inApp'] as const).map((channel) => (
                  <td key={channel} className="py-3 text-center">
                    <Checkbox
                      checked={item.mandatory ? true : value[channel]}
                      disabled={item.mandatory}
                      onCheckedChange={(checked) => set(item, channel, checked === true)}
                      aria-label={`${item.label} · ${t(`notifications.preferences.${channel}`)}`}
                    />
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
      {save.isError && (
        <p role="alert" className="text-destructive text-sm">
          {errorMessage(save.error, t)}
        </p>
      )}
      <div>
        <Button onClick={submit} disabled={!dirty || save.isPending}>
          {t('notifications.preferences.save')}
        </Button>
      </div>
    </div>
  )
}
