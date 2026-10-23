import { AlertTriangle, CheckCircle2, WifiOff, XCircle } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import type { ScanOutcome } from './checkInOutcome'

type Tone = 'success' | 'warning' | 'danger'

const TONES: Record<Tone, string> = {
  success: 'bg-success text-white',
  warning: 'bg-warning text-white',
  danger: 'bg-destructive text-white',
}

function toneOf(outcome: ScanOutcome): Tone {
  if (outcome.kind === 'valid') return 'success'
  // Vé thật nhưng không vào được lúc này (đã dùng, ngoài giờ check-in): vàng; vé có vấn đề: đỏ.
  return outcome.kind === 'used' || outcome.kind === 'notOpen' || outcome.kind === 'ended' ? 'warning' : 'danger'
}

function iconOf(outcome: ScanOutcome, tone: Tone) {
  if (outcome.kind === 'valid') return CheckCircle2
  if (outcome.kind === 'offline') return WifiOff
  return tone === 'warning' ? AlertTriangle : XCircle
}

/**
 * Kết quả một lần quét chiếm cả màn hình (docs/design/checkin.md §3): màu + biểu tượng + tiêu đề chữ lớn, nên không
 * chỉ dựa vào màu. Focus chuyển vào nút "Quét vé tiếp theo" để người soát vé bấm ngay.
 */
export function ScanResult({ outcome, onNext, onRetry }: { outcome: ScanOutcome; onNext: () => void; onRetry: () => void }) {
  const { t } = useTranslation()
  const next = useRef<HTMLButtonElement>(null)
  useEffect(() => next.current?.focus(), [outcome])

  const tone = toneOf(outcome)
  const Icon = iconOf(outcome, tone)
  const rows: Array<[string, string]> =
    outcome.kind === 'valid'
      ? [
          [t('checkin.seat'), outcome.seat],
          [t('checkin.ticketCode'), outcome.ticketId.slice(-8).toUpperCase()],
        ]
      : []

  return (
    <section role="alert" className={cn('flex flex-1 flex-col items-center justify-center gap-6 p-6 text-center', TONES[tone])}>
      <Icon className="size-20" aria-hidden="true" />
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold">{t(`checkin.result.${outcome.kind}.title`)}</h1>
        <p className="text-base opacity-90">{t(`checkin.result.${outcome.kind}.detail`)}</p>
      </div>
      {rows.length > 0 && (
        <dl className="w-full max-w-sm rounded-xl bg-white/15 p-4 text-left">
          {rows.map(([label, value]) => (
            <div key={label} className="flex justify-between gap-4 py-1">
              <dt className="opacity-80">{label}</dt>
              <dd className="font-semibold">{value}</dd>
            </div>
          ))}
        </dl>
      )}
      <div className="flex w-full max-w-sm flex-col gap-2">
        {(outcome.kind === 'offline' || outcome.kind === 'error') && (
          <Button size="lg" variant="outline" className="h-12 bg-transparent text-white hover:bg-white/10" onClick={onRetry}>
            {t('checkin.retry')}
          </Button>
        )}
        <Button ref={next} size="lg" variant="secondary" className="text-foreground h-12" onClick={onNext}>
          {t('checkin.next')}
        </Button>
      </div>
    </section>
  )
}
