import { useMutation } from '@tanstack/react-query'
import { useRef } from 'react'

import { createBooking } from '@/api/generated/endpoints/booking-controller/booking-controller'
import { ApiError, newIdempotencyKey } from '@/lib/api-client'
import { isRetryable } from '@/lib/error-messages'

/**
 * Giữ ghế (`POST /bookings`). Idempotency-Key sinh **một lần cho mỗi lần người dùng bấm** và giữ nguyên khi retry
 * sau lỗi mạng / 5xx, để request đã tới server không tạo thêm đơn (AC-7). Key bị bỏ khi có kết quả rõ ràng (thành
 * công hoặc 4xx) hoặc khi lựa chọn ghế đổi (`discardKey`): lần bấm sau là một thao tác mới.
 */
export function useHoldSeats(eventId: string) {
  const key = useRef<string | null>(null)

  const mutation = useMutation({
    mutationFn: (seatIds: string[]) => {
      key.current ??= newIdempotencyKey()
      return createBooking({ eventId, seatIds }, { 'Idempotency-Key': key.current })
    },
    onSuccess: () => {
      key.current = null
    },
    onError: (error) => {
      if (error instanceof ApiError && !isRetryable(error)) key.current = null
    },
  })

  return {
    hold: mutation.mutate,
    isPending: mutation.isPending,
    error: mutation.error,
    clearError: mutation.reset,
    discardKey: () => {
      key.current = null
    },
  }
}
