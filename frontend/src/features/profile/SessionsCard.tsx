import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  endMySession,
  getListMySessionsQueryKey,
  useEndMySession,
  useListMySessions,
} from '@/api/generated/endpoints/my-profile-controller/my-profile-controller'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatTime } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

/** Phiên đăng nhập (#19): đăng xuất từng phiên khác hoặc tất cả phiên khác; phiên hiện tại dùng nút Đăng xuất chung. */
export function SessionsCard() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const sessions = useListMySessions({ query: { staleTime: 0 } })
  const end = useEndMySession()
  const [endingOthers, setEndingOthers] = useState(false)
  const list = sessions.data?.status === 200 ? sessions.data.data : []
  const others = list.filter((session) => !session.current)

  const refresh = () => queryClient.invalidateQueries({ queryKey: getListMySessionsQueryKey() })
  const endOne = (sessionId: string) =>
    end.mutate({ sessionId }, { onSuccess: () => void refresh().then(() => toast.success(t('profile.sessions.ended'))) })
  const endAllOthers = async () => {
    setEndingOthers(true)
    try {
      await Promise.all(others.map((session) => endMySession(session.id)))
      toast.success(t('profile.sessions.endedOthers'))
    } catch (error) {
      toast.error(errorMessage(error, t))
    } finally {
      setEndingOthers(false)
      void refresh()
    }
  }

  return (
    <section aria-labelledby="sessions-heading" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="sessions-heading" className="text-sm font-semibold">
          {t('profile.sessions.heading')}
        </h3>
        <Button variant="outline" size="sm" disabled={others.length === 0 || endingOthers} onClick={() => void endAllOthers()}>
          {t('profile.sessions.endOthers')}
        </Button>
      </div>
      {sessions.isPending ? (
        <Skeleton className="h-16" aria-busy="true" />
      ) : sessions.isError ? (
        <div role="alert" className="flex items-center justify-between gap-3 text-sm">
          <span>{t('profile.sessions.loadError')}</span>
          <Button variant="outline" size="sm" onClick={() => void sessions.refetch()}>
            {t('common.retry')}
          </Button>
        </div>
      ) : (
        <ul aria-labelledby="sessions-heading" className="divide-y rounded-lg border">
          {list.map((session) => (
            <li key={session.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
              <div className="flex flex-col">
                <span className="font-medium">{session.ipAddress ?? t('profile.sessions.unknownIp')}</span>
                <span className="text-muted-foreground text-xs">
                  {t('profile.sessions.lastAccess', { time: `${formatTime(session.lastAccessAt)} · ${formatDate(session.lastAccessAt)}` })}
                </span>
              </div>
              {session.current ? (
                <Badge variant="outline">{t('profile.sessions.thisDevice')}</Badge>
              ) : (
                <Button variant="ghost" size="sm" disabled={end.isPending} onClick={() => endOne(session.id)}>
                  {t('profile.sessions.end')}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {end.isError && (
        <p role="alert" className="text-destructive text-sm">
          {errorMessage(end.error, t)}
        </p>
      )}
    </section>
  )
}
