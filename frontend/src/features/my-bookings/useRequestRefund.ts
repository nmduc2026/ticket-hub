import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'

import { requestBookingRefund } from '@/api/generated/endpoints/booking-controller/booking-controller'
import { ApiError, newIdempotencyKey } from '@/lib/api-client'
import { isRetryable } from '@/lib/error-messages'

/**
 * Yêu cầu hoàn vé (`POST /bookings/{id}/refund`). Idempotency-Key sinh một lần cho mỗi lần người dùng bấm và giữ
 * nguyên khi thử lại sau lỗi mạng / 5xx, để request đã tới server không tạo thêm yêu cầu (skill react mục 3). Key
 * bỏ đi khi có kết quả rõ ràng (thành công hoặc 4xx).
 */
export function useRequestRefund(bookingId: string, onDone: () => void) {
  const queryClient = useQueryClient()
  const key = useRef<string | null>(null)

  return useMutation({
    mutationFn: () => {
      key.current ??= newIdempotencyKey()
      return requestBookingRefund(bookingId, { headers: { 'Idempotency-Key': key.current } })
    },
    onSuccess: async () => {
      key.current = null
      // Đơn đổi trạng thái và vé bị thu hồi: tải lại cả hai danh sách.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['/api/v1/me/bookings'] }),
        queryClient.invalidateQueries({ queryKey: ['/api/v1/me/tickets'] }),
      ])
      onDone()
    },
    onError: (error) => {
      if (error instanceof ApiError && !isRetryable(error)) key.current = null
    },
  })
}
