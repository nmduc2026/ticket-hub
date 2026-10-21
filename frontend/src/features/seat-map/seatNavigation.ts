/** Phần của ghế cần để di chuyển bằng bàn phím. */
export interface NavSeat {
  id: string
  sectionCode: string
  row: string
  number: number
  x: number
  y: number
}

export type NavKey = 'ArrowLeft' | 'ArrowRight' | 'ArrowUp' | 'ArrowDown' | 'Home' | 'End' | 'PageUp' | 'PageDown'

export const NAV_KEYS: ReadonlySet<string> = new Set<NavKey>([
  'ArrowLeft',
  'ArrowRight',
  'ArrowUp',
  'ArrowDown',
  'Home',
  'End',
  'PageUp',
  'PageDown',
])

/**
 * Ghế nhận focus kế tiếp (docs/design/seat-map.md §4): trái / phải theo số ghế cùng hàng; lên / xuống sang hàng liền
 * kề, chọn ghế gần nhất theo chiều ngang; Home / End đầu / cuối hàng; PageUp / PageDown sang khu trước / sau.
 * Trả về chính ghế hiện tại khi không còn chỗ để đi.
 */
export function nextSeat(seats: NavSeat[], currentId: string, key: NavKey, sectionOrder: string[]): string {
  const current = seats.find((seat) => seat.id === currentId)
  if (!current) return currentId

  const sameSection = seats.filter((seat) => seat.sectionCode === current.sectionCode)
  const sameRow = sameSection.filter((seat) => seat.row === current.row).sort((a, b) => a.number - b.number)
  const position = sameRow.findIndex((seat) => seat.id === currentId)

  switch (key) {
    case 'ArrowLeft':
      return sameRow[Math.max(0, position - 1)].id
    case 'ArrowRight':
      return sameRow[Math.min(sameRow.length - 1, position + 1)].id
    case 'Home':
      return sameRow[0].id
    case 'End':
      return sameRow[sameRow.length - 1].id
    case 'ArrowUp':
    case 'ArrowDown':
      return adjacentRowSeat(sameSection, current, key === 'ArrowUp' ? -1 : 1)
    case 'PageUp':
    case 'PageDown':
      return adjacentSectionSeat(seats, current, key === 'PageUp' ? -1 : 1, sectionOrder)
  }
}

function adjacentRowSeat(sameSection: NavSeat[], current: NavSeat, direction: -1 | 1): string {
  const rowYs = [...new Set(sameSection.map((seat) => seat.y))].sort((a, b) => a - b)
  const targetY = rowYs[rowYs.indexOf(current.y) + direction]
  if (targetY === undefined) return current.id
  const candidates = sameSection.filter((seat) => seat.y === targetY)
  return candidates.reduce((best, seat) => (Math.abs(seat.x - current.x) < Math.abs(best.x - current.x) ? seat : best)).id
}

function adjacentSectionSeat(seats: NavSeat[], current: NavSeat, direction: -1 | 1, sectionOrder: string[]): string {
  const withSeats = sectionOrder.filter((code) => seats.some((seat) => seat.sectionCode === code))
  const targetCode = withSeats[withSeats.indexOf(current.sectionCode) + direction]
  if (targetCode === undefined) return current.id
  return firstSeat(seats.filter((seat) => seat.sectionCode === targetCode)).id
}

/** Ghế đầu tiên theo thứ tự đọc: hàng trên trước, trong hàng từ trái sang phải. */
export function firstSeat(seats: NavSeat[]): NavSeat {
  return seats.reduce((best, seat) => (seat.y < best.y || (seat.y === best.y && seat.x < best.x) ? seat : best))
}
