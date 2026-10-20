import { ExternalLink } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { MeResponse } from '@/api/generated/model'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { KEYCLOAK_ACCOUNT_URL } from '@/lib/config'
import { initials } from '@/lib/initials'

/**
 * Hồ sơ cá nhân (màn #19, chưa có cài đặt thông báo). Hồ sơ lấy từ `GET /me`; email, mật khẩu và phiên đăng nhập
 * do Keycloak quản lý nên các nút đổi dẫn tới trang tài khoản của Keycloak.
 */
export function ProfilePage({ user }: { user: MeResponse }) {
  const { t } = useTranslation()
  const fields: Array<{ label: string; value: string | null }> = [
    { label: t('profile.info.fullName'), value: user.fullName },
    { label: t('profile.info.username'), value: user.username },
    { label: t('profile.info.email'), value: user.email },
  ]

  return (
    <section className="mx-auto flex max-w-3xl flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">{t('profile.title')}</h1>
      <Tabs defaultValue="info">
        <TabsList>
          <TabsTrigger value="info">{t('profile.tabs.info')}</TabsTrigger>
          <TabsTrigger value="security">{t('profile.tabs.security')}</TabsTrigger>
        </TabsList>

        <TabsContent value="info" className="bg-card mt-4 flex flex-col gap-5 rounded-xl border p-6">
          <h2 className="text-base font-semibold">{t('profile.info.heading')}</h2>
          <div className="flex items-center gap-4">
            <Avatar size="lg">
              <AvatarFallback className="bg-primary/10 text-primary font-semibold">
                {initials(user.fullName, user.username)}
              </AvatarFallback>
            </Avatar>
            <div className="flex flex-wrap gap-1.5">
              {user.roles.map((role) => (
                <Badge key={role} variant="outline">
                  {t(`profile.roleNames.${role}`, { defaultValue: role })}
                </Badge>
              ))}
            </div>
          </div>
          <dl className="grid gap-4 sm:grid-cols-2">
            {fields.map((field) => (
              <div key={field.label} className="flex flex-col gap-1">
                <dt className="text-muted-foreground text-sm">{field.label}</dt>
                <dd className="text-sm font-medium break-words">{field.value ?? '—'}</dd>
              </div>
            ))}
          </dl>
          <div>
            <Button variant="outline" size="sm" asChild>
              <a href={KEYCLOAK_ACCOUNT_URL} target="_blank" rel="noreferrer">
                {t('profile.info.changeEmail')}
                <ExternalLink />
              </a>
            </Button>
          </div>
        </TabsContent>

        <TabsContent value="security" className="bg-card mt-4 flex flex-col gap-5 rounded-xl border p-6">
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
          <div className="flex items-center justify-between gap-4">
            <span className="text-sm font-medium">{t('profile.security.sessions')}</span>
            <Button variant="outline" size="sm" asChild>
              <a href={KEYCLOAK_ACCOUNT_URL} target="_blank" rel="noreferrer">
                {t('profile.security.manageSessions')}
                <ExternalLink />
              </a>
            </Button>
          </div>
        </TabsContent>
      </Tabs>
    </section>
  )
}
