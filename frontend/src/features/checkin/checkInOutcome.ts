import { ApiError } from '@/lib/api-client'

/** Kết quả một lần quét, theo bảng ở docs/design/checkin.md §3. */
export type ScanOutcome =
  | { kind: 'valid'; seat: string; ticketId: string }
  | { kind: 'used' }
  | { kind: 'revoked' }
  | { kind: 'forged' }
  | { kind: 'wrongEvent' }
  | { kind: 'notFound' }
  | { kind: 'notOwner' }
  | { kind: 'notOpen' }
  | { kind: 'ended' }
  | { kind: 'offline' }
  | { kind: 'error' }

/** Chuyển lỗi của `POST /checkins` thành kết quả hiển thị. Lỗi mạng (không có phản hồi) là "mất kết nối". */
export function outcomeFromError(error: unknown): ScanOutcome {
  if (!(error instanceof ApiError)) return { kind: 'offline' }
  switch (error.code) {
    case 'TICKET_ALREADY_USED':
      return { kind: 'used' }
    case 'TICKET_REVOKED':
      return { kind: 'revoked' }
    case 'TICKET_NOT_FOUND':
      return { kind: 'notFound' }
    case 'NOT_EVENT_OWNER':
      return { kind: 'notOwner' }
    case 'CHECKIN_NOT_OPEN':
      return { kind: 'notOpen' }
    case 'EVENT_ENDED':
      return { kind: 'ended' }
    default:
      return { kind: 'error' }
  }
}

/** Chấp nhận (cho vào) hay từ chối: quyết định màu, âm thanh và rung. */
export function isAccepted(outcome: ScanOutcome): boolean {
  return outcome.kind === 'valid'
}
