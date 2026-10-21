import type { TFunction } from 'i18next'

import { ApiError } from '@/lib/api-client'

/** Mã lỗi nghiệp vụ (Problem Details `code`) có thông báo riêng; mã khác dùng thông báo chung. */
const KNOWN_CODES = new Set([
  'SEAT_UNAVAILABLE',
  'MAX_SEATS_EXCEEDED',
  'TICKET_LIMIT_EXCEEDED',
  'EVENT_NOT_ON_SALE',
  'PENDING_BOOKING_EXISTS',
  'BOOKING_NOT_CANCELLABLE',
  'HOLD_EXPIRING',
  'BOOKING_NOT_PAYABLE',
  'BOOKING_NOT_FOUND',
  'EVENT_NOT_FOUND',
  'EVENT_LOCKED',
  'PUBLISH_NOT_ALLOWED',
  'PUBLISH_INCOMPLETE',
  'INVALID_EVENT_TIMES',
  'INVALID_EVENT_TRANSITION',
  'UNSUPPORTED_IMAGE_TYPE',
  'IMAGE_TOO_LARGE',
  'EMPTY_FILE',
  'TIER_NOT_FOUND',
  'SEAT_MAP_REQUIRED',
  'DUPLICATE_SECTION',
  'SECTION_NOT_IN_SEAT_MAP',
  'VENUE_REQUIRED',
  'VENUE_NOT_FOUND',
  'SEAT_MAP_NOT_IN_VENUE',
  'ORGANIZER_NOT_REGISTERED',
])

/**
 * Chuyển lỗi từ API sang thông báo tiếng Việt cho người dùng; chỗ duy nhất map mã lỗi (skill react mục 7).
 * Không bao giờ hiện mã kỹ thuật; `values` điền tham số của thông báo (vd. `max` của TICKET_LIMIT_EXCEEDED).
 */
export function errorMessage(error: unknown, t: TFunction, values: Record<string, unknown> = {}): string {
  if (error instanceof ApiError) {
    if (error.code && KNOWN_CODES.has(error.code)) return t(`errors.${error.code}`, values)
    if (error.status >= 500) return t('errors.server')
    return t('errors.generic')
  }
  return t('errors.network')
}

/** Lỗi mạng hoặc 5xx: yêu cầu có thể đã tới server, nên thử lại phải dùng đúng Idempotency-Key cũ. */
export function isRetryable(error: unknown): boolean {
  return !(error instanceof ApiError) || error.status >= 500
}
