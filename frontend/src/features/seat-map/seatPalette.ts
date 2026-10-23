/** Số màu hạng vé trong bảng cố định (docs/design/README.md §2); hạng thứ 7 trở đi dùng lại từ đầu. */
export const TIER_COLOR_COUNT = 6

export interface SeatPalette {
  tiers: string[]
  unavailable: string
  unavailableMark: string
  selected: string
  selectedStroke: string
  focus: string
  label: string
}

function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

/**
 * Canvas không đọc được biến CSS, nên đọc giá trị đã tính của token theme. Gọi lại khi đổi sáng / tối.
 * Màu ghế luôn theo token, không hard-code.
 */
export function readSeatPalette(): SeatPalette {
  return {
    tiers: Array.from({ length: TIER_COLOR_COUNT }, (_, index) => cssVar(`--tier-${index + 1}`)),
    unavailable: cssVar('--seat-off'),
    unavailableMark: cssVar('--seat-off-mark'),
    selected: cssVar('--primary'),
    selectedStroke: cssVar('--foreground'),
    focus: cssVar('--ring'),
    label: cssVar('--muted-foreground'),
  }
}
