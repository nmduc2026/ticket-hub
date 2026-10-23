import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getGetOrganizerProfileQueryKey, useGetOrganizerProfile } from '@/api/generated/endpoints/organizer-profile-controller/organizer-profile-controller'
import { NotificationPreferences } from '@/features/notifications/NotificationPreferences'
import { ApplicationForm } from '@/features/organizer-apply/ApplicationForm'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

/** Cài đặt organizer (màn #27): hồ sơ và pháp lý, thông báo. Thành viên của organizer ngoài phạm vi dự án. */
export function OrganizerSettingsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const query = useGetOrganizerProfile()
  const profile = query.data?.status === 200 ? query.data.data : undefined

  return (
    <div className="flex max-w-3xl flex-col gap-4">
      <h1 className="text-2xl font-semibold tracking-tight">{t('organizerSettings.title')}</h1>
      <Tabs defaultValue="profile">
        <TabsList>
          <TabsTrigger value="profile">{t('organizerSettings.tabProfile')}</TabsTrigger>
          <TabsTrigger value="notifications">{t('organizerSettings.tabNotifications')}</TabsTrigger>
        </TabsList>
        <TabsContent value="profile" className="mt-4 flex flex-col gap-3">
          <p className="text-muted-foreground text-sm">{t('organizerSettings.legalNote')}</p>
          {query.isPending && <Skeleton className="h-96 w-full" aria-busy="true" />}
          {profile && (
            <ApplicationForm
              key={profile.submittedAt}
              mode="update"
              previous={profile}
              submitLabel={t('organizerSettings.save')}
              onSubmitted={() => {
                toast.success(t('organizerSettings.saved'))
                void queryClient.invalidateQueries({ queryKey: getGetOrganizerProfileQueryKey() })
              }}
            />
          )}
        </TabsContent>
        <TabsContent value="notifications" className="mt-4">
          <NotificationPreferences />
        </TabsContent>
      </Tabs>
    </div>
  )
}
