import { Link } from '@tanstack/react-router'
import { Scanner } from '@yudiel/react-qr-scanner'
import type { IDetectedBarcode, IScannerError } from '@yudiel/react-qr-scanner'
import { ChevronLeft, ScanLine, Volume2, VolumeX, WifiOff } from 'lucide-react'
import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'

import { useGetOrganizerEvent } from '@/api/generated/endpoints/organizer-event-controller/organizer-event-controller'
import { useGetEventSales } from '@/api/generated/endpoints/organizer-sales-controller/organizer-sales-controller'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { isAccepted, type ScanOutcome } from './checkInOutcome'
import { ScanResult } from './ScanResult'
import { playAccepted, playRejected, unlockSound } from './sound'
import { useCheckInScan } from './useCheckInScan'
import { useOnline } from './useOnline'
import { useStoredSetting } from './useStoredSetting'

const MAX_GATE_LENGTH = 50
/** Các cổng khác cùng soát vé: số đếm tự làm mới theo chu kỳ này, ngoài lần làm mới sau mỗi vé hợp lệ ở cổng này. */
const COUNTER_REFRESH_MS = 30_000

/** Check-in tại cổng (màn #10): quét QR bằng camera, xác minh chữ ký rồi xác nhận với server (docs/design/checkin.md). */
export function CheckInPage({ eventId }: { eventId: string }) {
  const { t } = useTranslation()
  const online = useOnline()
  const [gate, setGate] = useStoredSetting('checkin.gate', '')
  const [sound, setSound] = useStoredSetting('checkin.sound', 'on')
  const [gateDraft, setGateDraft] = useState(gate)
  const [started, setStarted] = useState(false)
  const [outcome, setOutcome] = useState<ScanOutcome | null>(null)
  const [cameraError, setCameraError] = useState<IScannerError | null>(null)
  const [cameraKey, setCameraKey] = useState(0)
  const busy = useRef(false)
  const lastToken = useRef('')

  const eventQuery = useGetOrganizerEvent(eventId, { query: { staleTime: 60_000, retry: false } })
  const title = eventQuery.data?.status === 200 ? eventQuery.data.data.title : undefined
  const scan = useCheckInScan(eventId, gate)
  // Số đếm chỉ là thông tin phụ: lỗi hay đang tải thì ẩn, không ảnh hưởng việc quét vé.
  const salesQuery = useGetEventSales(eventId, { query: { retry: false, refetchInterval: COUNTER_REFRESH_MS } })
  const sales = salesQuery.data?.status === 200 ? salesQuery.data.data : undefined

  const start = (event: FormEvent) => {
    event.preventDefault()
    const trimmed = gateDraft.trim()
    if (!trimmed) return
    setGate(trimmed)
    unlockSound()
    setStarted(true)
  }

  const scanToken = async (token: string) => {
    if (busy.current) return
    busy.current = true
    lastToken.current = token
    const result = await scan(token)
    if (isAccepted(result)) void salesQuery.refetch()
    if (sound === 'on') (isAccepted(result) ? playAccepted : playRejected)()
    setOutcome(result)
    busy.current = false
  }

  const next = () => {
    setOutcome(null)
    lastToken.current = ''
  }
  const retry = () => {
    const token = lastToken.current
    setOutcome(null)
    if (token) void scanToken(token)
  }
  const onScan = (codes: IDetectedBarcode[]) => {
    const token = codes[0]?.rawValue
    if (token) void scanToken(token)
  }

  return (
    <div className="bg-background text-foreground flex min-h-dvh flex-col">
      <header className="flex h-14 items-center gap-1 border-b px-2">
        <Button variant="ghost" size="icon" className="size-11" asChild aria-label={t('checkin.back')}>
          <Link to="/organizer/events/$eventId" params={{ eventId }}>
            <ChevronLeft />
          </Link>
        </Button>
        <div className="min-w-0 flex-1">
          <div className="truncate text-[15px] font-semibold">
            {gate ? t('checkin.headerWithGate', { gate }) : t('checkin.header')}
          </div>
          {title && <div className="text-muted-foreground truncate text-xs">{title}</div>}
          {sales && (
            <div className="text-muted-foreground truncate text-xs tabular-nums" data-testid="checkin-counter">
              {t('checkin.counter', { checkedIn: sales.checkedIn.toLocaleString('vi-VN'), sold: sales.ticketsSold.toLocaleString('vi-VN') })}
            </div>
          )}
        </div>
        <Button
          variant="ghost"
          size="icon"
          className="size-11"
          aria-label={sound === 'on' ? t('checkin.soundOff') : t('checkin.soundOn')}
          aria-pressed={sound === 'on'}
          onClick={() => setSound(sound === 'on' ? 'off' : 'on')}
        >
          {sound === 'on' ? <Volume2 /> : <VolumeX />}
        </Button>
      </header>

      {!online && (
        <p role="alert" className="bg-destructive flex items-center gap-2 px-4 py-2 text-sm font-medium text-white">
          <WifiOff className="size-4 shrink-0" aria-hidden="true" />
          {t('checkin.offlineBanner')}
        </p>
      )}

      {!started ? (
        <form onSubmit={start} className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-4 p-6">
          <ScanLine className="text-primary size-12" aria-hidden="true" />
          <h1 className="text-xl font-semibold">{t('checkin.setupTitle')}</h1>
          <div className="flex flex-col gap-2">
            <Label htmlFor="checkin-gate">{t('checkin.gateLabel')}</Label>
            <Input
              id="checkin-gate"
              value={gateDraft}
              maxLength={MAX_GATE_LENGTH}
              placeholder={t('checkin.gatePlaceholder')}
              onChange={(event) => setGateDraft(event.target.value)}
              className="h-12"
              required
            />
          </div>
          <Button type="submit" size="lg" className="h-12" disabled={!gateDraft.trim()}>
            {t('checkin.start')}
          </Button>
        </form>
      ) : outcome ? (
        <ScanResult outcome={outcome} onNext={next} onRetry={retry} />
      ) : cameraError ? (
        <div role="alert" className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-4 p-6 text-center">
          <h1 className="text-xl font-semibold">
            {t(`checkin.camera.${cameraError.kind}.title`, { defaultValue: t('checkin.camera.unknown.title') })}
          </h1>
          <p className="text-muted-foreground text-sm">
            {t(`checkin.camera.${cameraError.kind}.detail`, { defaultValue: t('checkin.camera.unknown.detail') })}
          </p>
          <Button
            size="lg"
            className="h-12"
            onClick={() => {
              setCameraError(null)
              setCameraKey((key) => key + 1)
            }}
          >
            {t('checkin.camera.retry')}
          </Button>
        </div>
      ) : (
        <div className="relative flex flex-1 flex-col">
          <Scanner
            key={cameraKey}
            onScan={onScan}
            onError={setCameraError}
            paused={!online}
            formats={['qr_code']}
            constraints={{ facingMode: 'environment' }}
            sound={false}
            styles={{ container: { flex: 1, width: '100%' } }}
          />
          <p className="absolute inset-x-0 bottom-6 text-center text-sm font-medium text-white drop-shadow" aria-live="polite">
            {online ? t('checkin.scanning') : t('checkin.paused')}
          </p>
        </div>
      )}
    </div>
  )
}
