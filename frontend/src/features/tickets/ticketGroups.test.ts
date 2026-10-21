import type { Ticket } from '@/api/generated/model'

import { ticketTab } from './ticketGroups'

const base: Ticket = {
  ticketId: 't1',
  eventId: 'e1',
  eventTitle: 'Đêm nhạc',
  startsAt: '2026-11-15T13:00:00Z',
  venueName: 'Nhà hát',
  seat: { sectionCode: 'A', row: 'B', number: 7 },
  tierName: 'VIP',
  status: 'VALID',
  qrToken: 'jwt',
}
const now = new Date('2026-10-20T00:00:00Z').getTime()

describe('ticketTab', () => {
  it('vé hợp lệ của sự kiện chưa diễn ra là sắp diễn ra', () => {
    expect(ticketTab(base, now)).toBe('upcoming')
  })
  it('vé đã dùng hoặc sự kiện đã bắt đầu là đã qua', () => {
    expect(ticketTab({ ...base, status: 'USED' }, now)).toBe('past')
    expect(ticketTab({ ...base, startsAt: '2026-10-01T13:00:00Z' }, now)).toBe('past')
  })
  it('vé bị thu hồi là đã hoàn', () => {
    expect(ticketTab({ ...base, status: 'REVOKED' }, now)).toBe('refunded')
  })
})
