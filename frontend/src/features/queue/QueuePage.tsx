import { Link, useNavigate } from '@tanstack/react-router'
import { Check, Info } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import { useGetEvent } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import type { PublicEventDetail } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCountdown, useCountdown } from '@/lib/use-countdown'
import { formatDate, formatTime } from '@/lib/date'
import { EventPoster } from '@/components/EventPoster'

import { progressPercent, type QueueView } from './queueState'
import { useQueueRoom } from './useQueueRoom'

/** Sau khi được gọi tên, dừng ngắn ở "Đến lượt bạn" rồi tự sang chọn ghế. */
const AUTO_CONTINUE_MS = 3_000

/** Phòng chờ (màn #4): vị trí, thời gian chờ dự kiến, tự chuyển sang chọn ghế khi đến lượt. */
export function QueuePage({ eventId }: { eventId: string }) {
  const { t } = useTranslation()
  const view = useQueueRoom(eventId)
  const event = useGetEvent(eventId, { query: { staleTime: 15_000, retry: false } })
  const detail = event.data?.status === 200 ? event.data.data : undefined

  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col items-center gap-6 py-8">
      <main className="bg-card w-full overflow-hidden rounded-xl border shadow-sm">
        <EventHeader detail={detail} />
        <QueueBody view={view} eventId={eventId} saleStartsAt={detail?.saleStartsAt} soldOut={detail?.soldOut ?? false} />
        {(view.kind === 'queued' || view.kind === 'lottery') && (
          <div className="text-muted-foreground mx-6 mt-4 mb-6 flex gap-2.5 rounded-lg border p-3 text-[13px] leading-relaxed">
            <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>{t('queue.keepsPlace')}</span>
          </div>
        )}
      </main>
      <Link to="/events/$eventId" params={{ eventId }} className="text-muted-foreground hover:text-foreground text-[13px] underline underline-offset-4">
        {t('queue.leave')}
      </Link>
    </div>
  )
}

function EventHeader({ detail }: { detail: PublicEventDetail | undefined }) {
  if (!detail) {
    return (
      <div className="flex items-center gap-3 border-b px-6 py-4" aria-busy="true">
        <Skeleton className="size-12 rounded-md" />
        <Skeleton className="h-5 w-2/3" />
      </div>
    )
  }
  return (
    <div className="flex items-center gap-3 border-b px-6 py-4">
      <div className="size-12 shrink-0 overflow-hidden rounded-md">
        <EventPoster title={detail.title} bannerUrl={detail.bannerUrl} />
      </div>
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="truncate font-semibold">{detail.title}</span>
        <span className="text-muted-foreground truncate text-[13px]">
          {formatDate(detail.startsAt)} · {formatTime(detail.startsAt)} · {detail.venueName}
        </span>
      </div>
    </div>
  )
}

function QueueBody({ view, eventId, saleStartsAt, soldOut }: { view: QueueView; eventId: string; saleStartsAt?: string; soldOut: boolean }) {
  const { t } = useTranslation()

  switch (view.kind) {
    case 'joining':
      return (
        <div className="flex flex-col gap-3 px-6 pt-7 pb-2" aria-busy="true">
          <Skeleton className="h-6 w-1/2" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-2 w-full" />
        </div>
      )
    case 'lottery':
      return (
        <Section title={t('queue.lotteryTitle')} body={t('queue.lotteryBody')}>
          {saleStartsAt && <Row label={t('queue.saleStartsAt')} value={`${formatTime(saleStartsAt)} · ${formatDate(saleStartsAt)}`} />}
          {soldOut && <SoldOutNote />}
        </Section>
      )
    case 'queued':
      return <QueuedBody view={view} soldOut={soldOut} />
    case 'admitted':
      return <AdmittedBody expiresAt={view.expiresAt} eventId={eventId} />
    case 'closed':
      return <Section title={t(`queue.closed.${view.reason}.title`)} body={t(`queue.closed.${view.reason}.body`)} />
    case 'unavailable':
      return <UnavailableBody reason={view.reason} eventId={eventId} />
  }
}

function QueuedBody({ view, soldOut }: { view: Extract<QueueView, { kind: 'queued' }>; soldOut: boolean }) {
  const { t } = useTranslation()
  const percent = progressPercent(view)
  const minutes = Math.ceil(view.etaSeconds / 60)

  return (
    <Section title={t('queue.waitingTitle')} body={t('queue.waitingBody')}>
      <div className="flex flex-col gap-2">
        <div
          role="progressbar"
          aria-label={t('queue.progress')}
          aria-valuenow={percent}
          aria-valuemin={0}
          aria-valuemax={100}
          className="bg-secondary h-2 overflow-hidden rounded-full"
        >
          <div className="bg-primary h-full transition-[width]" style={{ width: `${percent}%` }} />
        </div>
        <div className="text-muted-foreground flex justify-between text-[13px]">
          <span>{t('queue.progressStart')}</span>
          <span>{t('queue.progressEnd')}</span>
        </div>
      </div>
      <div>
        <Row label={t('queue.ahead')} value={Math.max(0, view.position - 1).toLocaleString('vi-VN')} />
        <Row label={t('queue.eta')} value={minutes <= 1 ? t('queue.etaUnderMinute') : t('queue.etaMinutes', { count: minutes })} />
        <Row label={t('queue.updatedAt')} value={new Date(view.updatedAt).toLocaleTimeString('vi-VN', { hour12: false, timeZone: 'Asia/Ho_Chi_Minh' })} />
      </div>
      {soldOut && <SoldOutNote />}
    </Section>
  )
}

function AdmittedBody({ expiresAt, eventId }: { expiresAt: string; eventId: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const seconds = useCountdown(expiresAt)

  useEffect(() => {
    const timer = setTimeout(
      () => void navigate({ to: '/events/$eventId/seats', params: { eventId }, replace: true }),
      AUTO_CONTINUE_MS,
    )
    return () => clearTimeout(timer)
  }, [eventId, navigate])

  return (
    <div className="flex flex-col gap-4 px-6 pt-7 pb-6">
      <div className="flex items-center gap-2.5">
        <span className="flex size-7 items-center justify-center rounded-full bg-green-700 text-white">
          <Check className="size-4" aria-hidden="true" />
        </span>
        <h1 className="text-xl font-semibold">{t('queue.admittedTitle')}</h1>
      </div>
      <p className="text-muted-foreground leading-relaxed">{t('queue.admittedBody')}</p>
      {seconds != null && (
        <p role="timer" className="text-sm font-medium tabular-nums">
          {t('queue.admittedLeft', { time: formatCountdown(seconds) })}
        </p>
      )}
      <Button asChild size="lg">
        <Link to="/events/$eventId/seats" params={{ eventId }} replace>
          {t('queue.pickSeats')}
        </Link>
      </Button>
    </div>
  )
}

function UnavailableBody({ reason, eventId }: { reason: 'NOT_OPEN' | 'NOT_ENABLED' | 'NOT_FOUND' | 'ERROR'; eventId: string }) {
  const { t } = useTranslation()
  return (
    <Section title={t(`queue.unavailable.${reason}.title`)} body={t(`queue.unavailable.${reason}.body`)}>
      {reason === 'ERROR' && (
        <Button variant="outline" size="sm" className="self-start" onClick={() => window.location.reload()}>
          {t('common.retry')}
        </Button>
      )}
      {reason === 'NOT_ENABLED' && (
        <Button asChild className="self-start">
          <Link to="/events/$eventId/seats" params={{ eventId }}>
            {t('queue.pickSeats')}
          </Link>
        </Button>
      )}
    </Section>
  )
}

function Section({ title, body, children }: { title: string; body: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-5 px-6 pt-7 pb-2">
      <div className="flex flex-col gap-1.5">
        <h1 className="text-xl font-semibold">{title}</h1>
        <p className="text-muted-foreground leading-relaxed">{body}</p>
      </div>
      {children}
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b py-3 text-sm last:border-b-0">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-semibold tabular-nums">{value}</span>
    </div>
  )
}

function SoldOutNote() {
  const { t } = useTranslation()
  return (
    <p role="status" className="bg-secondary rounded-lg px-3 py-2 text-[13px]">
      {t('queue.soldOut')}
    </p>
  )
}
