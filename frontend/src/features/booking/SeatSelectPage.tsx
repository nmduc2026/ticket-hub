import { useNavigate } from '@tanstack/react-router'
import { AlertCircle, WifiOff } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useGetEvent, useGetEventSeats } from '@/api/generated/endpoints/public-event-controller/public-event-controller'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { SeatMap, useSeatSelection } from '@/features/seat-map'
import { ApiError } from '@/lib/api-client'
import { MAX_SEATS_PER_BOOKING } from '@/lib/config'
import { formatEventRange } from '@/lib/date'
import { errorMessage } from '@/lib/error-messages'

import { SelectionPanel } from './SelectionPanel'
import { useHoldSeats } from './useHoldSeats'

const SEAT_POLL_MS = 5000

/**
 * Chọn ghế và giữ ghế (màn #5). Trạng thái ghế lấy bằng polling 5 giây tới Sprint 7 (SSE), dừng khi tab ẩn
 * (docs/design/seat-map.md §3). Lỗi chặn luồng hiện ngay trong trang theo bảng ở §2 cùng file.
 */
export function SeatSelectPage({ eventId }: { eventId: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const event = useGetEvent(eventId, { query: { staleTime: 15_000, retry: false } })
  const seats = useGetEventSeats(eventId, {
    query: { staleTime: 0, refetchInterval: SEAT_POLL_MS, refetchIntervalInBackground: false },
  })
  const hold = useHoldSeats(eventId)
  // Lấy trước các early return bên dưới: TypeScript thu hẹp `event` / `seats` theo nhánh thành công.
  const { refetch: refetchEvent } = event
  const { refetch: refetchSeats } = seats
  const { selectedIds, open, set, retain } = useSeatSelection()
  const [limitReached, setLimitReached] = useState(false)
  const [lostSeats, setLostSeats] = useState<string[]>([])

  useEffect(() => open(eventId), [eventId, open])

  const detail = event.data?.status === 200 ? event.data.data : undefined
  const snapshot = seats.data?.status === 200 ? seats.data.data : undefined
  const maxSelect = Math.min(MAX_SEATS_PER_BOOKING, detail?.maxTicketsPerUser ?? MAX_SEATS_PER_BOOKING)
  const onSale = snapshot?.eventStatus === 'ON_SALE' && detail?.status === 'ON_SALE'

  // Ghế đang chọn mà người khác vừa giữ / bán thì bỏ khỏi lựa chọn và báo (seat-map.md §3).
  useEffect(() => {
    if (!snapshot) return
    const available = new Set(
      snapshot.seats
        .filter((seat) => seat.status === 'AVAILABLE' && snapshot.sections.some((s) => s.code === seat.sectionCode && s.tierId))
        .map((seat) => seat.id),
    )
    const lost = snapshot.seats.filter((seat) => selectedIds.has(seat.id) && !available.has(seat.id))
    if (lost.length > 0) {
      retain(available)
      setLostSeats(lost.map((seat) => t('seatMap.seat', { row: seat.row, number: seat.number })))
    }
  }, [snapshot, selectedIds, retain, t])

  const selectedSeats = useMemo(
    () => (snapshot ? snapshot.seats.filter((seat) => selectedIds.has(seat.id)) : []),
    [snapshot, selectedIds],
  )

  // Đã có sơ đồ thì lỗi khi cập nhật chỉ hiện banner mất kết nối (bên dưới), không thay cả trang.
  if (!detail || !snapshot) {
    if (event.isPending || seats.isPending) return <PageSkeleton />
    const status = [event.error, seats.error].find((error) => error instanceof ApiError)?.status
    return (
      <div role="alert" className="mx-auto flex max-w-lg flex-col items-center gap-3 py-16 text-center">
        <AlertCircle className="text-muted-foreground size-10" aria-hidden="true" />
        <h1 className="text-xl font-semibold">
          {status === 404 ? t('eventDetail.notFoundTitle') : t('seatPage.loadError')}
        </h1>
        {status !== 404 && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              void refetchEvent()
              void refetchSeats()
            }}
          >
            {t('common.retry')}
          </Button>
        )}
      </div>
    )
  }

  const isOffline = seats.isRefetchError
  const holdError = hold.error ? errorMessage(hold.error, t, { max: detail.maxTicketsPerUser }) : null
  const banners = [
    !onSale && { id: 'not-on-sale', text: t('seatPage.notOnSale') },
    lostSeats.length > 0 && { id: 'lost', text: t('seatPage.lostSeats', { seats: lostSeats.join('; ') }) },
    holdError && { id: 'hold', text: holdError },
  ].filter((banner): banner is { id: string; text: string } => Boolean(banner))

  const changeSelection = (next: ReadonlySet<string>) => {
    set(next)
    hold.discardKey()
    hold.clearError()
    setLimitReached(false)
    setLostSeats([])
  }

  const submit = () => {
    setLostSeats([])
    hold.hold([...selectedIds], {
      onSuccess: (response) => {
        if (response.status === 201) {
          useSeatSelection.getState().clear()
          void navigate({ to: '/checkout/$bookingId', params: { bookingId: response.data.bookingId } })
        }
      },
      onError: (error) => {
        // Server là nguồn sự thật: tải lại trạng thái ghế / sự kiện để thấy ghế nào không còn trống.
        if (error instanceof ApiError && error.code === 'EVENT_NOT_ON_SALE') void refetchEvent()
        void refetchSeats()
      },
    })
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-4">
      <header className="flex flex-col gap-1">
        <p className="text-muted-foreground text-sm">{t('seatPage.step')}</p>
        <h1 className="text-2xl font-semibold tracking-tight">{detail.title}</h1>
        <p className="text-muted-foreground text-sm">
          {formatEventRange(detail.startsAt, detail.endsAt)} · {detail.venueName}
        </p>
      </header>

      {isOffline && (
        <div role="alert" className="bg-muted flex flex-wrap items-center gap-3 rounded-lg border px-4 py-2.5 text-sm">
          <WifiOff className="size-4 shrink-0" aria-hidden="true" />
          <span className="flex-1">{t('seatPage.offline')}</span>
          <Button variant="outline" size="sm" onClick={() => void refetchSeats()}>
            {t('seatPage.retryNow')}
          </Button>
        </div>
      )}
      {banners.map((banner) => (
        <div key={banner.id} role="alert" className="border-destructive/40 bg-destructive/5 rounded-lg border px-4 py-2.5 text-sm">
          {banner.text}
        </div>
      ))}

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <section aria-label={t('seatMap.label')} className="flex min-w-0 flex-col gap-3">
          <SeatMap
            snapshot={snapshot}
            selectedIds={selectedIds}
            maxSelect={maxSelect}
            onSelectionChange={changeSelection}
            onLimitReached={() => setLimitReached(true)}
            disabled={!onSale || hold.isPending}
          />
          <SeatLegend />
        </section>
        <SelectionPanel
          tiers={snapshot.tiers}
          sections={snapshot.sections}
          selectedSeats={selectedSeats}
          maxSelect={maxSelect}
          limitReached={limitReached}
          canContinue={onSale && !isOffline && selectedSeats.length > 0}
          isPending={hold.isPending}
          onRemove={(seatId) => changeSelection(new Set([...selectedIds].filter((id) => id !== seatId)))}
          onContinue={submit}
        />
      </div>
    </div>
  )
}

function SeatLegend() {
  const { t } = useTranslation()
  const swatch = 'inline-block size-3.5 rounded-sm'
  return (
    <ul className="text-muted-foreground flex flex-wrap gap-x-5 gap-y-1 text-sm">
      <li className="flex items-center gap-1.5">
        <span className={swatch} style={{ backgroundColor: 'var(--tier-1)' }} aria-hidden="true" />
        {t('seatMap.legend.available')}
      </li>
      <li className="flex items-center gap-1.5">
        <span className={`${swatch} bg-primary border-foreground border-2`} aria-hidden="true" />
        {t('seatMap.legend.selected')}
      </li>
      <li className="flex items-center gap-1.5">
        <span className={swatch} style={{ backgroundColor: 'var(--seat-off)' }} aria-hidden="true" />
        {t('seatMap.legend.sold')}
      </li>
    </ul>
  )
}

function PageSkeleton() {
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-4" aria-busy="true">
      <Skeleton className="h-16 w-1/2" />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Skeleton className="h-[28rem]" />
        <Skeleton className="h-[28rem]" />
      </div>
    </div>
  )
}
