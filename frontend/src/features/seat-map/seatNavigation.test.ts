import { firstSeat, type NavSeat, nextSeat } from './seatNavigation'

/** Khu A: hàng A (y=100) và B (y=126), mỗi hàng 3 ghế; khu B: 1 hàng 2 ghế. */
const seats: NavSeat[] = [
  ...['A', 'B'].flatMap((row, rowIndex) =>
    [1, 2, 3].map((number) => ({
      id: `A-${row}${number}`,
      sectionCode: 'A',
      row,
      number,
      x: 60 + (number - 1) * 24,
      y: 100 + rowIndex * 26,
    })),
  ),
  { id: 'B-A1', sectionCode: 'B', row: 'A', number: 1, x: 300, y: 100 },
  { id: 'B-A2', sectionCode: 'B', row: 'A', number: 2, x: 324, y: 100 },
]
const order = ['A', 'B']

describe('nextSeat', () => {
  it('trái / phải đi theo số ghế cùng hàng và dừng ở đầu / cuối hàng', () => {
    expect(nextSeat(seats, 'A-A2', 'ArrowRight', order)).toBe('A-A3')
    expect(nextSeat(seats, 'A-A3', 'ArrowRight', order)).toBe('A-A3')
    expect(nextSeat(seats, 'A-A2', 'ArrowLeft', order)).toBe('A-A1')
    expect(nextSeat(seats, 'A-A1', 'ArrowLeft', order)).toBe('A-A1')
  })

  it('lên / xuống sang hàng liền kề, giữ cột gần nhất, dừng ở hàng đầu / cuối', () => {
    expect(nextSeat(seats, 'A-A2', 'ArrowDown', order)).toBe('A-B2')
    expect(nextSeat(seats, 'A-B3', 'ArrowUp', order)).toBe('A-A3')
    expect(nextSeat(seats, 'A-A1', 'ArrowUp', order)).toBe('A-A1')
    expect(nextSeat(seats, 'A-B1', 'ArrowDown', order)).toBe('A-B1')
  })

  it('Home / End nhảy tới đầu / cuối hàng', () => {
    expect(nextSeat(seats, 'A-B2', 'Home', order)).toBe('A-B1')
    expect(nextSeat(seats, 'A-B2', 'End', order)).toBe('A-B3')
  })

  it('PageDown / PageUp sang ghế đầu của khu sau / trước', () => {
    expect(nextSeat(seats, 'A-B2', 'PageDown', order)).toBe('B-A1')
    expect(nextSeat(seats, 'B-A2', 'PageUp', order)).toBe('A-A1')
    expect(nextSeat(seats, 'B-A2', 'PageDown', order)).toBe('B-A2')
  })

  it('ghế không tồn tại thì giữ nguyên', () => {
    expect(nextSeat(seats, 'nope', 'ArrowRight', order)).toBe('nope')
  })
})

describe('firstSeat', () => {
  it('là ghế trên cùng, trái nhất', () => {
    expect(firstSeat(seats).id).toBe('A-A1')
  })
})
