import { decodeProtectedHeader, importJWK, jwtVerify } from 'jose'

import type { SigningKey } from '@/api/generated/model'

export type QrVerification =
  | { ok: true; ticketId: string; eventId: string }
  | { ok: false; reason: 'FORGED' | 'WRONG_EVENT' }

/**
 * Xác minh chữ ký QR ES256 ngay trên máy (overview.md §7.5, ADR 0024): loại vé giả mà không cần gọi server. Chữ ký đúng
 * chỉ chứng minh vé do Ticket Hub phát; vé còn dùng được hay không do server quyết định khi check-in. Khoá chọn theo
 * `kid` của header nên vé phát trước khi xoay khoá vẫn xác minh được.
 */
export async function verifyTicketQr(
  token: string,
  keys: readonly SigningKey[],
  expectedEventId: string,
): Promise<QrVerification> {
  try {
    const kid = decodeProtectedHeader(token).kid
    const key = keys.find((candidate) => candidate.kid === kid)
    if (!kid || !key) return { ok: false, reason: 'FORGED' }

    const publicKey = await importJWK({ kty: key.kty, crv: key.crv, x: key.x, y: key.y }, 'ES256')
    const { payload } = await jwtVerify(token, publicKey, { algorithms: ['ES256'] })
    const { ticketId, eventId } = payload
    if (typeof ticketId !== 'string' || typeof eventId !== 'string') return { ok: false, reason: 'FORGED' }
    if (eventId !== expectedEventId) return { ok: false, reason: 'WRONG_EVENT' }
    return { ok: true, ticketId, eventId }
  } catch {
    // Không phải JWT, chữ ký sai, thuật toán lạ...: với người soát vé đều là vé giả.
    return { ok: false, reason: 'FORGED' }
  }
}
