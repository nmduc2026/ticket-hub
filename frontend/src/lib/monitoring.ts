import * as Sentry from '@sentry/react'
import { onCLS, onINP, onLCP, type Metric } from 'web-vitals'

/** DSN của Sentry (gói free) đặt lúc build; trống thì không gửi gì (dev, test). */
const SENTRY_DSN: string = import.meta.env.VITE_SENTRY_DSN ?? ''

/** Lỗi JS chưa bắt (router, render) báo về Sentry; không làm gì khi chưa cấu hình DSN. */
export function captureError(error: unknown): void {
  if (SENTRY_DSN) {
    Sentry.captureException(error)
  }
}

/** Core Web Vitals: ghi breadcrumb cho mọi chỉ số, chỉ báo thành sự kiện khi chỉ số ở mức `poor`. */
export function reportVital(metric: Metric): void {
  if (!SENTRY_DSN) {
    return
  }
  Sentry.addBreadcrumb({
    category: 'web-vital',
    level: metric.rating === 'poor' ? 'warning' : 'info',
    message: `${metric.name}=${Math.round(metric.value)}`,
    data: { rating: metric.rating, id: metric.id },
  })
  if (metric.rating === 'poor') {
    Sentry.captureMessage(`Web vital kém: ${metric.name}`, 'warning')
  }
}

export function initMonitoring(): void {
  if (!SENTRY_DSN) {
    return
  }
  Sentry.init({
    dsn: SENTRY_DSN,
    environment: import.meta.env.MODE,
    // Traces do Jaeger lo, Sentry chỉ nhận lỗi.
    tracesSampleRate: 0,
  })
  onLCP(reportVital)
  onINP(reportVital)
  onCLS(reportVital)
}
