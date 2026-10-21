import { timeRangeFor } from './timeRange'

describe('timeRangeFor (giờ Việt Nam)', () => {
  // 2026-11-12 là thứ Năm
  const thursday = new Date('2026-11-12T05:00:00Z')

  it('"all" không giới hạn', () => {
    expect(timeRangeFor('all', thursday)).toEqual({})
    expect(timeRangeFor(undefined, thursday)).toEqual({})
  })

  it('hôm nay là từ 00:00 đến 00:00 hôm sau theo +07:00', () => {
    expect(timeRangeFor('today', thursday)).toEqual({ from: '2026-11-11T17:00:00.000Z', to: '2026-11-12T17:00:00.000Z' })
  })

  it('cuối tuần này là thứ Bảy 00:00 đến thứ Hai 00:00', () => {
    expect(timeRangeFor('weekend', thursday)).toEqual({
      from: '2026-11-13T17:00:00.000Z', // thứ Bảy 14/11 00:00 +07:00
      to: '2026-11-15T17:00:00.000Z', // thứ Hai 16/11 00:00 +07:00
    })
  })

  it('vào Chủ nhật thì cuối tuần chỉ còn hôm nay', () => {
    const sunday = new Date('2026-11-15T05:00:00Z')
    expect(timeRangeFor('weekend', sunday)).toEqual({ from: '2026-11-14T17:00:00.000Z', to: '2026-11-15T17:00:00.000Z' })
  })

  it('tháng này là mùng 1 đến mùng 1 tháng sau', () => {
    expect(timeRangeFor('month', thursday)).toEqual({ from: '2026-10-31T17:00:00.000Z', to: '2026-11-30T17:00:00.000Z' })
  })
})
