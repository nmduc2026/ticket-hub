import { AlertCircle, ExternalLink } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useGetMyProfile } from '@/api/generated/endpoints/my-profile-controller/my-profile-controller'
import type { MeResponse } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { NotificationPreferences } from '@/features/notifications/NotificationPreferences'
import { KEYCLOAK_ACCOUNT_URL } from '@/lib/config'

import { DeleteAccountCard } from './DeleteAccountCard'
import { ProfileInfoCard } from './ProfileInfoCard'
import { SessionsCard } from './SessionsCard'

/**
 * Hồ sơ cá nhân (màn #19). SĐT, ngày sinh, tỉnh / thành, ảnh đại diện, phiên đăng nhập và xoá tài khoản qua API của
 * app; họ tên, email, mật khẩu do Keycloak giữ nên dẫn tới trang tài khoản của Keycloak.
 */
export function ProfilePage({ user }: { user: MeResponse }) {
  const { t } = useTranslation()
  const profile = useGetMyProfile({ query: { staleTime: 60_000 } })
  const data = profile.data?.status === 200 ? profile.data.data : undefined

  return (
    <section className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">{t('profile.title')}</h1>
        <div className="flex flex-wrap gap-1.5">
          {user.roles.map((role) => (
            <Badge key={role} variant="outline">
              {t(`profile.roleNames.${role}`, { defaultValue: role })}
            </Badge>
          ))}
        </div>
      </div>
      <Tabs defaultValue="info">
        <TabsList>
          <TabsTrigger value="info">{t('profile.tabs.info')}</TabsTrigger>
          <TabsTrigger value="notifications">{t('profile.tabs.notifications')}</TabsTrigger>
          <TabsTrigger value="security">{t('profile.tabs.security')}</TabsTrigger>
        </TabsList>

        <TabsContent value="info" className="bg-card mt-4 rounded-xl border p-6">
          {profile.isPending ? (
            <Skeleton className="h-64" aria-busy="true" />
          ) : data ? (
            <ProfileInfoCard key={data.userId} profile={data} />
          ) : (
            <StatusPanel icon={<AlertCircle />} title={t('profile.loadError')}>
              <Button variant="outline" size="sm" onClick={() => void profile.refetch()}>
                {t('common.retry')}
              </Button>
            </StatusPanel>
          )}
        </TabsContent>

        <TabsContent value="notifications" className="bg-card mt-4 rounded-xl border p-6">
          <NotificationPreferences />
        </TabsContent>

        <TabsContent value="security" className="bg-card mt-4 flex flex-col gap-6 rounded-xl border p-6">
          <h2 className="text-base font-semibold">{t('profile.security.heading')}</h2>
          <div className="flex items-center justify-between gap-4">
            <span className="text-sm font-medium">{t('profile.security.password')}</span>
            <Button variant="outline" size="sm" asChild>
              <a href={KEYCLOAK_ACCOUNT_URL} target="_blank" rel="noreferrer">
                {t('profile.security.changePassword')}
                <ExternalLink />
              </a>
            </Button>
          </div>
          <SessionsCard />
          <DeleteAccountCard email={data?.email ?? user.email ?? undefined} />
        </TabsContent>
      </Tabs>
    </section>
  )
}
