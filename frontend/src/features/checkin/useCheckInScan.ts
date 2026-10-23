import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'

import { checkIn, getCheckInKeys } from '@/api/generated/endpoints/check-in-controller/check-in-controller'
import type { SigningKey } from '@/api/generated/model'

import { outcomeFromError, type ScanOutcome } from './checkInOutcome'
import { verifyTicketQr } from './qrVerify'

const KEYS_STALE_TIME = 10 * 60_000

/**
 * Xử lý một mã QR vừa quét (docs/design/checkin.md §2): xác minh chữ ký trên máy bằng khoá công khai (loại vé giả,
 * không gọi server), rồi xác nhận với server. Chỉ `200` mới là hợp lệ; mất mạng ở bất kỳ bước nào cũng không cho vào.
 */
export function useCheckInScan(eventId: string, gate: string) {
  const queryClient = useQueryClient()

  const loadKeys = useCallback(
    async (force: boolean): Promise<readonly SigningKey[]> => {
      const options = {
        queryKey: ['checkin-keys'],
        queryFn: async () => {
          const response = await getCheckInKeys()
          return response.status === 200 ? response.data.keys : []
        },
        staleTime: force ? 0 : KEYS_STALE_TIME,
      }
      return queryClient.fetchQuery(options)
    },
    [queryClient],
  )

  return useCallback(
    async (token: string): Promise<ScanOutcome> => {
      try {
        let verification = await verifyTicketQr(token, await loadKeys(false), eventId)
        if (!verification.ok && verification.reason === 'FORGED') {
          // Khoá có thể vừa xoay: tải lại danh sách khoá một lần trước khi kết luận là vé giả.
          verification = await verifyTicketQr(token, await loadKeys(true), eventId)
        }
        if (!verification.ok) return { kind: verification.reason === 'FORGED' ? 'forged' : 'wrongEvent' }

        const response = await checkIn({ qrToken: token, gate })
        return response.status === 200
          ? { kind: 'valid', seat: response.data.seat, ticketId: response.data.ticketId }
          : { kind: 'error' }
      } catch (error) {
        return outcomeFromError(error)
      }
    },
    [eventId, gate, loadKeys],
  )
}
