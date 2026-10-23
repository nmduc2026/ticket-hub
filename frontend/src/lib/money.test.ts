import { formatMoney } from './money'

describe('formatMoney', () => {
  it('VND là đơn vị nguyên, không nhân hay chia 100 (BR-10)', () => {
    expect(formatMoney(800000, 'VND').replace(/\s/g, ' ')).toBe('800.000 ₫')
    expect(formatMoney(50000).replace(/\s/g, ' ')).toBe('50.000 ₫')
    expect(formatMoney(0).replace(/\s/g, ' ')).toBe('0 ₫')
  })

  it('tiền có hai chữ số thập phân thì đổi từ đơn vị nhỏ nhất', () => {
    expect(formatMoney(1050, 'USD').replace(/\s/g, ' ')).toContain('10,50')
  })
})
