import type { OrganizerEventDetail } from '@/api/generated/model'

import { publishChecklist } from './publishChecklist'

const draft: OrganizerEventDetail = {
  id: 'e1',
  title: 'Sự kiện',
  status: 'DRAFT',
  maxTicketsPerUser: 6,
  inventoryReady: false,
  salesInfoLocked: false,
  tiers: [],
  tierSections: [],
}

const missing = (event: OrganizerEventDetail) =>
  publishChecklist(event)
    .filter((entry) => !entry.done)
    .map((entry) => entry.item)

describe('publishChecklist', () => {
  it('bản nháp trống thiếu mọi mục, đúng mã của backend', () => {
    expect(missing(draft)).toEqual(['VENUE', 'SEAT_MAP', 'STARTS_AT', 'ENDS_AT', 'SALE_STARTS_AT', 'TICKET_TIERS', 'TIER_SECTIONS'])
  })

  it('đủ thông tin, hạng vé và gán khu thì không thiếu gì', () => {
    const complete: OrganizerEventDetail = {
      ...draft,
      venueId: 'v',
      seatMapId: 'm',
      startsAt: '2026-11-15T13:00:00Z',
      endsAt: '2026-11-15T15:00:00Z',
      saleStartsAt: '2026-10-01T00:00:00Z',
      tiers: [{ id: 't', name: 'VIP', priceMinor: 1, currency: 'VND' }],
      tierSections: [{ sectionId: 's', tierId: 't' }],
    }
    expect(missing(complete)).toEqual([])
  })

  it('có hạng vé nhưng chưa gán khu thì chỉ thiếu TIER_SECTIONS, và chỉ tới tab hạng vé', () => {
    const event: OrganizerEventDetail = {
      ...draft,
      venueId: 'v',
      seatMapId: 'm',
      startsAt: 'x',
      endsAt: 'x',
      saleStartsAt: 'x',
      tiers: [{ id: 't', name: 'VIP', priceMinor: 1, currency: 'VND' }],
    }
    expect(missing(event)).toEqual(['TIER_SECTIONS'])
    expect(publishChecklist(event).find((entry) => entry.item === 'TIER_SECTIONS')?.tab).toBe('tiers')
  })
})
