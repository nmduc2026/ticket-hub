import { formatDate, formatDateChip, formatEventRange, formatTime, startOfVnDay, startOfVnMonth, isoToVnInputs, vnInputsToIso } from './date'

describe('hiển thị giờ theo Asia/Ho_Chi_Minh', () => {
  it('đổi UTC sang giờ Việt Nam (+07:00), kể cả khi sang ngày mới', () => {
    // 2026-11-15T13:00:00Z = 20:00 ngày 15/11 ở Việt Nam
    expect(formatTime('2026-11-15T13:00:00Z')).toBe('20:00')
    expect(formatDate('2026-11-15T13:00:00Z')).toBe('15/11/2026')
    // 2026-11-15T18:30:00Z = 01:30 ngày 16/11 ở Việt Nam
    expect(formatDate('2026-11-15T18:30:00Z')).toBe('16/11/2026')
    expect(formatTime('2026-11-15T18:30:00Z')).toBe('01:30')
  })

  it('chip ngày có thứ viết tắt: 15/11/2026 là Chủ nhật', () => {
    expect(formatDateChip('2026-11-15T13:00:00Z')).toBe('CN, 15/11')
    expect(formatDateChip('2026-11-14T13:00:00Z')).toBe('T7, 14/11')
  })

  it('khoảng giờ của sự kiện', () => {
    expect(formatEventRange('2026-11-14T13:00:00Z', '2026-11-14T15:30:00Z')).toMatch(
      /^Thứ Bảy, 14\/11\/2026 · 20:00–22:30$/,
    )
  })

  it('đầu ngày và đầu tháng theo giờ Việt Nam được trả về dưới dạng UTC', () => {
    const reference = new Date('2026-11-15T18:30:00Z') // 16/11 01:30 giờ Việt Nam
    expect(startOfVnDay(reference)).toBe('2026-11-15T17:00:00.000Z') // 16/11 00:00 +07:00
    expect(startOfVnDay(reference, 1)).toBe('2026-11-16T17:00:00.000Z')
    expect(startOfVnMonth(reference)).toBe('2026-10-31T17:00:00.000Z') // 01/11 00:00 +07:00
    expect(startOfVnMonth(reference, 1)).toBe('2026-11-30T17:00:00.000Z')
  })
})

describe('ngày giờ nhập theo giờ Việt Nam', () => {
  it('20:00 giờ Việt Nam là 13:00 UTC', () => {
    expect(vnInputsToIso('2026-11-15', '20:00')).toBe('2026-11-15T13:00:00.000Z')
  })

  it('00:30 giờ Việt Nam thuộc ngày hôm trước theo UTC', () => {
    expect(vnInputsToIso('2026-11-15', '00:30')).toBe('2026-11-14T17:30:00.000Z')
  })

  it('thiếu hoặc sai định dạng thì không có giá trị', () => {
    expect(vnInputsToIso('', '20:00')).toBeUndefined()
    expect(vnInputsToIso('2026-11-15', '')).toBeUndefined()
  })

  it('đổi qua lại giữ nguyên giá trị', () => {
    const iso = '2026-11-15T13:00:00.000Z'
    const { date, time } = isoToVnInputs(iso)
    expect(date).toBe('2026-11-15')
    expect(time).toBe('20:00')
    expect(vnInputsToIso(date, time)).toBe(iso)
    expect(isoToVnInputs(undefined)).toEqual({ date: '', time: '' })
  })
})
