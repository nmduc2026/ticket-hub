import { exportJWK, generateKeyPair, SignJWT } from 'jose'

import type { SigningKey } from '@/api/generated/model'

import { verifyTicketQr } from './qrVerify'

const EVENT = '00000000-0000-7000-8000-0000000e0001'
const TICKET = '00000000-0000-7000-8000-0000000c0001'

async function newKey(kid: string) {
  const { publicKey, privateKey } = await generateKeyPair('ES256')
  const jwk = await exportJWK(publicKey)
  const signingKey: SigningKey = { kid, kty: 'EC', crv: 'P-256', x: String(jwk.x), y: String(jwk.y) }
  return { privateKey, signingKey }
}

async function sign(privateKey: CryptoKey, kid: string, claims: Record<string, unknown> = { ticketId: TICKET, eventId: EVENT }) {
  return new SignJWT(claims).setProtectedHeader({ alg: 'ES256', kid }).sign(privateKey)
}

describe('verifyTicketQr', () => {
  it('QR ký bằng khoá có trong danh sách và đúng sự kiện thì hợp lệ', async () => {
    const { privateKey, signingKey } = await newKey('k1')
    const token = await sign(privateKey, 'k1')

    expect(await verifyTicketQr(token, [signingKey], EVENT)).toEqual({ ok: true, ticketId: TICKET, eventId: EVENT })
  })

  it('xoay khoá: chọn đúng khoá theo kid trong danh sách có cả khoá cũ và mới', async () => {
    const oldKey = await newKey('old')
    const newer = await newKey('new')
    const token = await sign(oldKey.privateKey, 'old')

    const result = await verifyTicketQr(token, [newer.signingKey, oldKey.signingKey], EVENT)

    expect(result.ok).toBe(true)
  })

  it('AC-7: ký bằng khoá lạ nhưng mạo kid hợp lệ thì là vé giả', async () => {
    const real = await newKey('k1')
    const stranger = await newKey('k1')
    const token = await sign(stranger.privateKey, 'k1')

    expect(await verifyTicketQr(token, [real.signingKey], EVENT)).toEqual({ ok: false, reason: 'FORGED' })
  })

  it('kid lạ, thiếu kid, sửa nội dung, không phải JWT, chuỗi rỗng đều là vé giả', async () => {
    const { privateKey, signingKey } = await newKey('k1')
    const good = await sign(privateKey, 'k1')
    const [header, payload, signature] = good.split('.')
    const tamperedPayload = btoa(JSON.stringify({ ticketId: TICKET, eventId: 'su-kien-khac' }))
      .replaceAll('+', '-')
      .replaceAll('/', '_')
      .replaceAll('=', '')

    const unknownKid = await sign(privateKey, 'unknown')
    const noKid = await new SignJWT({ ticketId: TICKET, eventId: EVENT }).setProtectedHeader({ alg: 'ES256' }).sign(privateKey)

    for (const token of [unknownKid, noKid, `${header}.${tamperedPayload}.${signature}`, `${header}.${payload}`, 'khong-phai-jwt', '']) {
      expect(await verifyTicketQr(token, [signingKey], EVENT)).toEqual({ ok: false, reason: 'FORGED' })
    }
  })

  it('thiếu claim ticketId / eventId là vé giả', async () => {
    const { privateKey, signingKey } = await newKey('k1')

    expect(await verifyTicketQr(await sign(privateKey, 'k1', { eventId: EVENT }), [signingKey], EVENT)).toEqual({
      ok: false,
      reason: 'FORGED',
    })
  })

  it('chữ ký đúng nhưng của sự kiện khác thì báo sai sự kiện', async () => {
    const { privateKey, signingKey } = await newKey('k1')
    const token = await sign(privateKey, 'k1', { ticketId: TICKET, eventId: 'su-kien-khac' })

    expect(await verifyTicketQr(token, [signingKey], EVENT)).toEqual({ ok: false, reason: 'WRONG_EVENT' })
  })
})
