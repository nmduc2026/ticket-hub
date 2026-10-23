/**
 * Sinh ghế của một khu `GRID` từ tham số (ADR 0030, overview.md §6.1). Hàm thuần: editor, màn chọn ghế và backend
 * (bản Java `SeatGenerator`) phải ra cùng toạ độ cho cùng tham số; hai bên dùng chung bộ dữ liệu
 * `shared-test-data/generate-seats.json`.
 *
 * Quy ước:
 * - Ghế (hàng r, cột c) có toạ độ cục bộ `(c * seatGap, r * rowGap - curve * (1 - t²))`, với `t` chạy từ -1 tới 1 dọc
 *   hàng (0 khi hàng chỉ có 1 ghế): hàng cong lên (y giảm) ở giữa một đoạn `curve`. `curve` âm thì cong xuống.
 * - Xoay `rotation` độ theo chiều kim đồng hồ (trục y hướng xuống) quanh ghế đầu tiên, rồi dịch tới `(x, y)`.
 * - Nhãn hàng A, B, … Z, AA, AB, …; số ghế tăng từ trái sang phải (`LTR`) hoặc ngược lại (`RTL`).
 * - Ghế ẩn (`hiddenSeats`, vd. `"A2"`) bị bỏ khỏi kết quả nhưng số của các ghế còn lại giữ nguyên.
 * - Toạ độ làm tròn 2 chữ số thập phân.
 */
export interface GridParams {
  x: number
  y: number
  rotation: number
  rows: number
  seatsPerRow: number
  rowGap: number
  seatGap: number
  curve: number
  numbering: 'LTR' | 'RTL'
  hiddenSeats: readonly string[]
}

export interface GeneratedSeat {
  rowLabel: string
  seatNumber: number
  x: number
  y: number
}

/** A, B, …, Z, AA, AB, … (chỉ số bắt đầu từ 0). */
export function rowLabel(index: number): string {
  let label = ''
  let rest = index + 1
  while (rest > 0) {
    const remainder = (rest - 1) % 26
    label = String.fromCharCode(65 + remainder) + label
    rest = Math.floor((rest - 1) / 26)
  }
  return label
}

function round2(value: number): number {
  const rounded = Math.floor(value * 100 + 0.5) / 100
  return rounded === 0 ? 0 : rounded
}

export function generateSeats(params: GridParams): GeneratedSeat[] {
  const { x, y, rotation, rows, seatsPerRow, rowGap, seatGap, curve, numbering } = params
  const hidden = new Set(params.hiddenSeats)
  const theta = (rotation * Math.PI) / 180
  const cos = Math.cos(theta)
  const sin = Math.sin(theta)
  const half = (seatsPerRow - 1) / 2
  const seats: GeneratedSeat[] = []
  for (let row = 0; row < rows; row++) {
    const label = rowLabel(row)
    for (let column = 0; column < seatsPerRow; column++) {
      const seatNumber = numbering === 'RTL' ? seatsPerRow - column : column + 1
      if (hidden.has(`${label}${seatNumber}`)) continue
      const t = seatsPerRow === 1 ? 0 : (column - half) / half
      const localX = column * seatGap
      const localY = row * rowGap - curve * (1 - t * t)
      seats.push({
        rowLabel: label,
        seatNumber,
        x: round2(x + localX * cos - localY * sin),
        y: round2(y + localX * sin + localY * cos),
      })
    }
  }
  return seats
}
