import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'

import { createPayment } from '@/api/generated/endpoints/payment-controller/payment-controller'
import { newIdempotencyKey } from '@/lib/api-client'

/**
 * Bắt đầu thanh toán (`POST /payments`) ngay khi vào màn. Idempotency-Key sinh một lần cho mỗi lần vào màn và giữ khi
 * thử lại, nên server trả lại đúng `clientSecret` cũ (một payment cho một booking, AC-3).
 */
export function useStartPayment(bookingId: string, enabled: boolean) {
  const key = useRef(newIdempotencyKey())
  const mutation = useMutation({
    mutationFn: () => createPayment({ bookingId }, { 'Idempotency-Key': key.current }),
  })
  const { mutate, isIdle } = mutation

  useEffect(() => {
    if (enabled && isIdle) mutate()
  }, [enabled, isIdle, mutate])

  const payment = mutation.data?.status === 201 ? mutation.data.data : undefined
  return { payment, error: mutation.error, isPending: mutation.isPending || (enabled && mutation.isIdle), retry: mutate }
}
