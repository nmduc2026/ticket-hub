import { buildIcs } from './calendar'

describe('buildIcs (AC-9)', () => {
  const ics = buildIcs(
    {
      uid: 't1',
      title: 'Đêm nhạc Rock, Sài Gòn',
      location: 'Nhà hát Hòa Bình',
      startsAt: '2026-11-15T13:00:00Z',
      endsAt: '2026-11-15T15:30:00Z',
      description: 'Hàng B · Ghế 7',
    },
    new Date('2026-10-22T00:00:00Z'),
  )

  it('giờ diễn ghi theo Asia/Ho_Chi_Minh: 13:00Z là 20:00 giờ Việt Nam', () => {
    expect(ics).toContain('DTSTART;TZID=Asia/Ho_Chi_Minh:20261115T200000')
    expect(ics).toContain('DTEND;TZID=Asia/Ho_Chi_Minh:20261115T223000')
    expect(ics).toContain('TZOFFSETTO:+0700')
  })

  it('thoát dấu phẩy trong tiêu đề, dòng kết thúc bằng CRLF', () => {
    expect(ics).toContain('SUMMARY:Đêm nhạc Rock\\, Sài Gòn')
    expect(ics.split('\r\n')[0]).toBe('BEGIN:VCALENDAR')
    expect(ics.endsWith('END:VCALENDAR\r\n')).toBe(true)
  })

  it('không có giờ kết thúc thì bỏ DTEND', () => {
    const noEnd = buildIcs({ uid: 't2', title: 'A', location: 'B', startsAt: '2026-11-15T13:00:00Z' })
    expect(noEnd).not.toContain('DTEND')
  })
})
