import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { exportJWK, generateKeyPair, SignJWT } from 'jose'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { meResponse, mockMe, server } from '@/test/server'

const EVENT = '00000000-0000-7000-8000-0000000e0001'
const TICKET = '00000000-0000-7000-8000-0000000c0001'

// Camera không có trong jsdom: Scanner giả có nút "đọc mã" gửi đi đúng chuỗi test muốn quét.
let nextToken = ''
vi.mock('@yudiel/react-qr-scanner', () => ({
  Scanner: ({ onScan }: { onScan: (codes: Array<{ rawValue: string }>) => void }) => (
    <button type="button" onClick={() => onScan([{ rawValue: nextToken }])}>
      đọc mã
    </button>
  ),
}))

let privateKey: CryptoKey
let keyset: { kid: string; kty: string; crv: string; x: string; y: string }

beforeAll(async () => {
  const pair = await generateKeyPair('ES256')
  privateKey = pair.privateKey
  const jwk = await exportJWK(pair.publicKey)
  keyset = { kid: 'k1', kty: 'EC', crv: 'P-256', x: String(jwk.x), y: String(jwk.y) }
})

const token = (claims: Record<string, unknown> = { ticketId: TICKET, eventId: EVENT }) =>
  new SignJWT(claims).setProtectedHeader({ alg: 'ES256', kid: 'k1' }).sign(privateKey)

function renderCheckIn() {
  const queryClient = createQueryClient()
  const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [`/checkin/${EVENT}`] }))
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

async function startScanning() {
  const user = userEvent.setup()
  renderCheckIn()
  await user.type(await screen.findByLabelText('Cổng soát vé', undefined, { timeout: 5000 }), 'Cổng 2')
  await user.click(screen.getByRole('button', { name: 'Bắt đầu quét' }))
  return user
}

let checkInCalls = 0

beforeEach(() => {
  checkInCalls = 0
  localStorage.clear()
  mockMe(meResponse(['CUSTOMER', 'ORGANIZER']))
  server.use(
    http.get('/api/v1/checkin/keys', () => HttpResponse.json({ keys: [keyset] })),
    http.get(`/api/v1/organizer/events/${EVENT}`, () => HttpResponse.json({ id: EVENT, title: 'Đêm nhạc Rock Sài Gòn' })),
    http.get(`/api/v1/organizer/events/${EVENT}/sales`, () =>
      HttpResponse.json({ eventId: EVENT, currency: 'VND', grossMinor: 0, feeMinor: 0, refundedMinor: 0, netMinor: 0, ticketsSold: 1200, capacity: 1500, checkedIn: 312, byTier: [], byDay: [] }),
    ),
  )
})

describe('Check-in tại cổng', () => {
  it('vé hợp lệ: xác minh chữ ký, server xác nhận, hiện xanh kèm vị trí và gửi đúng cổng', async () => {
    let body: unknown
    server.use(
      http.post('/api/v1/checkins', async ({ request }) => {
        checkInCalls++
        body = await request.json()
        return HttpResponse.json({ ticketId: TICKET, seat: 'A-B-7', status: 'USED' })
      }),
    )
    nextToken = await token()
    const user = await startScanning()

    await user.click(await screen.findByRole('button', { name: 'đọc mã' }))

    expect(await screen.findByRole('heading', { name: 'Hợp lệ' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('A-B-7')).toBeInTheDocument()
    expect(body).toEqual({ qrToken: nextToken, gate: 'Cổng 2' })
    expect(await screen.findByText('Check-in · Cổng 2')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Quét vé tiếp theo' }))
    expect(await screen.findByRole('button', { name: 'đọc mã' })).toBeInTheDocument()
  })

  it('AC-7: QR chữ ký giả bị từ chối ngay, không gọi server', async () => {
    server.use(http.post('/api/v1/checkins', () => (checkInCalls++, HttpResponse.json({}))))
    const stranger = await generateKeyPair('ES256')
    nextToken = await new SignJWT({ ticketId: TICKET, eventId: EVENT })
      .setProtectedHeader({ alg: 'ES256', kid: 'k1' })
      .sign(stranger.privateKey)
    const user = await startScanning()

    await user.click(await screen.findByRole('button', { name: 'đọc mã' }))

    expect(await screen.findByRole('heading', { name: 'Vé không hợp lệ' }, { timeout: 5000 })).toBeInTheDocument()
    expect(checkInCalls).toBe(0)
  })

  it('QR của sự kiện khác bị từ chối, không gọi server', async () => {
    server.use(http.post('/api/v1/checkins', () => (checkInCalls++, HttpResponse.json({}))))
    nextToken = await token({ ticketId: TICKET, eventId: 'su-kien-khac' })
    const user = await startScanning()

    await user.click(await screen.findByRole('button', { name: 'đọc mã' }))

    expect(await screen.findByRole('heading', { name: 'Vé của sự kiện khác' }, { timeout: 5000 })).toBeInTheDocument()
    expect(checkInCalls).toBe(0)
  })

  it.each([
    ['TICKET_ALREADY_USED', 409, 'Vé đã được sử dụng'],
    ['TICKET_REVOKED', 409, 'Vé đã bị thu hồi'],
    ['TICKET_NOT_FOUND', 404, 'Không tìm thấy vé'],
    ['NOT_EVENT_OWNER', 403, 'Không có quyền soát vé'],
    ['CHECKIN_NOT_OPEN', 422, 'Chưa mở check-in'],
    ['EVENT_ENDED', 422, 'Sự kiện đã kết thúc'],
  ])('server trả %s thì hiện "%s" và không cho vào', async (code, status, heading) => {
    server.use(
      http.post('/api/v1/checkins', () =>
        HttpResponse.json({ type: 'about:blank', title: code, status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    )
    nextToken = await token()
    const user = await startScanning()

    await user.click(await screen.findByRole('button', { name: 'đọc mã' }))

    expect(await screen.findByRole('heading', { name: heading }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Hợp lệ' })).not.toBeInTheDocument()
  })

  it('AC-7: mất mạng khi xác nhận thì báo lỗi, không cho vào; "Quét lại" thử lại cùng mã', async () => {
    let online = false
    server.use(
      http.post('/api/v1/checkins', () => {
        checkInCalls++
        return online ? HttpResponse.json({ ticketId: TICKET, seat: 'A-B-7', status: 'USED' }) : HttpResponse.error()
      }),
    )
    nextToken = await token()
    const user = await startScanning()

    await user.click(await screen.findByRole('button', { name: 'đọc mã' }))

    expect(await screen.findByRole('heading', { name: 'Mất kết nối' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Hợp lệ' })).not.toBeInTheDocument()

    online = true
    await user.click(screen.getByRole('button', { name: 'Quét lại' }))
    expect(await screen.findByRole('heading', { name: 'Hợp lệ' }, { timeout: 5000 })).toBeInTheDocument()
    expect(checkInCalls).toBe(2)
  })

  it('cần nhập cổng mới bắt đầu quét được', async () => {
    renderCheckIn()

    expect(await screen.findByRole('button', { name: 'Bắt đầu quét' }, { timeout: 5000 })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'đọc mã' })).not.toBeInTheDocument()
  })
})
