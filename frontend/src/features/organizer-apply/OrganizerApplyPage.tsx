import { useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { AlertCircle, CheckCircle2, Clock } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import {
  getGetOrganizerApplicationQueryKey,
  useGetOrganizerApplication,
} from '@/api/generated/endpoints/organizer-application-controller/organizer-application-controller'
import type { MeResponse } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { hasRole } from '@/features/auth/roles'
import { ApiError } from '@/lib/api-client'
import { formatDate, formatTime } from '@/lib/date'

import { ApplicationForm } from './ApplicationForm'

/**
 * Đăng ký làm ban tổ chức (màn #22) với 4 trạng thái: form, chờ duyệt, bị từ chối (kèm lý do, nộp lại được), đã duyệt.
 * Role mới có trong token sau lần refresh kế tiếp nên đã duyệt mà chưa thấy menu thì nhắc đăng nhập lại.
 */
export function OrganizerApplyPage({ user }: { user: MeResponse }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const query = useGetOrganizerApplication({ query: { retry: false } })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: getGetOrganizerApplicationQueryKey() })

  const header = (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">{t('organizerApply.title')}</h1>
      <p className="text-muted-foreground text-sm">{t('organizerApply.subtitle')}</p>
    </div>
  )

  if (hasRole(user.roles, 'ORGANIZER')) {
    return (
      <Layout>
        {header}
        <Approved canOpen />
      </Layout>
    )
  }
  if (query.isPending) {
    return (
      <Layout>
        {header}
        <Skeleton className="h-80 w-full" aria-busy="true" />
      </Layout>
    )
  }

  const notSubmitted = query.error instanceof ApiError && query.error.status === 404
  if (query.isError && !notSubmitted) {
    return (
      <Layout>
        {header}
        <StatusPanel icon={<AlertCircle />} title={t('organizerApply.loadError')}>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        </StatusPanel>
      </Layout>
    )
  }

  const application = query.data?.status === 200 ? query.data.data : undefined
  if (!application) {
    return (
      <Layout>
        {header}
        <ApplicationForm submitLabel={t('organizerApply.form.submit')} onSubmitted={refresh} />
      </Layout>
    )
  }

  switch (application.status) {
    case 'PENDING':
      return (
        <Layout>
          {header}
          <StatusPanel icon={<Clock />} title={t('organizerApply.pending.title')}>
            <p className="text-sm">{t('organizerApply.pending.body', { name: application.displayName })}</p>
            <p className="text-muted-foreground text-xs">
              {t('organizerApply.pending.submittedAt', {
                time: `${formatDate(application.submittedAt)} · ${formatTime(application.submittedAt)}`,
              })}
            </p>
          </StatusPanel>
        </Layout>
      )
    case 'REJECTED':
      return (
        <Layout>
          {header}
          <div role="status" className="border-destructive/40 bg-destructive/5 flex flex-col gap-1 rounded-xl border p-4">
            <p className="font-semibold">{t('organizerApply.rejected.title')}</p>
            <p className="text-muted-foreground text-xs">{t('organizerApply.rejected.reason')}</p>
            <p className="text-sm">{application.rejectionReason}</p>
            <p className="text-muted-foreground text-sm">{t('organizerApply.rejected.body')}</p>
          </div>
          <ApplicationForm previous={application} submitLabel={t('organizerApply.form.resubmit')} onSubmitted={refresh} />
        </Layout>
      )
    default:
      return (
        <Layout>
          {header}
          <Approved />
        </Layout>
      )
  }
}

function Layout({ children }: { children: ReactNode }) {
  return <section className="mx-auto flex max-w-3xl flex-col gap-6">{children}</section>
}

function Approved({ canOpen = false }: { canOpen?: boolean }) {
  const { t } = useTranslation()
  return (
    <StatusPanel icon={<CheckCircle2 />} title={t('organizerApply.approved.title')}>
      <p className="text-sm">{t('organizerApply.approved.body')}</p>
      {canOpen && (
        <Button size="sm" asChild>
          <Link to="/organizer">{t('organizerApply.approved.open')}</Link>
        </Button>
      )}
    </StatusPanel>
  )
}
