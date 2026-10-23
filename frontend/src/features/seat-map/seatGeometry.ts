import { z } from 'zod'

/** Kích thước vẽ của một ghế trên sơ đồ (đơn vị toạ độ sơ đồ, cùng hệ với `seats.x, y`). */
export const SEAT_SIZE = 18

const SECTION_PADDING = 14

/**
 * `shape` của khu / phần tử là JSON tự do ở backend (OpenAPI khai object tự do), nên đọc bằng schema thay vì
 * tin kiểu sinh ra. Khu `GRID` có tham số lưới (ADR 0030); khu `IMPORTED` chỉ có toạ độ ghế nên không có khung.
 */
const gridShapeSchema = z.object({
  kind: z.literal('GRID'),
  x: z.number(),
  y: z.number(),
  rows: z.number().int().positive(),
  seatsPerRow: z.number().int().positive(),
  rowGap: z.number().positive(),
  seatGap: z.number().positive(),
  rotation: z.number().default(0),
  curve: z.number().default(0),
})

const elementShapeSchema = z.object({
  x: z.number(),
  y: z.number(),
  w: z.number().positive(),
  h: z.number().positive(),
  rotation: z.number().optional(),
})

export interface Rect {
  x: number
  y: number
  width: number
  height: number
}

/** Khung bao quanh khu `GRID` (có đệm quanh ghế); `null` khi không đủ thông tin (vd. khu IMPORTED). */
export function sectionRect(shape: unknown): Rect | null {
  const parsed = gridShapeSchema.safeParse(shape)
  if (!parsed.success) return null
  const { x, y, rows, seatsPerRow, rowGap, seatGap } = parsed.data
  return {
    x: x - SEAT_SIZE / 2 - SECTION_PADDING,
    y: y - SEAT_SIZE / 2 - SECTION_PADDING,
    width: (seatsPerRow - 1) * seatGap + SEAT_SIZE + 2 * SECTION_PADDING,
    height: (rows - 1) * rowGap + SEAT_SIZE + 2 * SECTION_PADDING,
  }
}

/** Khung của khu: vẽ trong nhóm đặt tại `(x, y)` (ghế đầu tiên) và xoay `rotation` độ, đúng như `generateSeats`. */
export interface SectionFrame {
  x: number
  y: number
  rotation: number
  /** Toạ độ cục bộ của nhóm, đã nới theo độ cong của hàng. */
  rect: Rect
}

/** Khung khu `GRID` kể cả xoay và hàng cong; `null` với khu `IMPORTED` hoặc shape lạ (dùng {@link seatsRect}). */
export function sectionFrame(shape: unknown): SectionFrame | null {
  const parsed = gridShapeSchema.safeParse(shape)
  if (!parsed.success) return null
  const { x, y, rows, seatsPerRow, rowGap, seatGap, rotation, curve } = parsed.data
  const pad = SEAT_SIZE / 2 + SECTION_PADDING
  return {
    x,
    y,
    rotation,
    rect: {
      x: -pad,
      y: -pad - Math.max(curve, 0),
      width: (seatsPerRow - 1) * seatGap + 2 * pad,
      height: (rows - 1) * rowGap + 2 * pad + Math.abs(curve),
    },
  }
}

/** Khung bao quanh một nhóm ghế (khu `IMPORTED` không có tham số lưới); `null` khi không có ghế. */
export function seatsRect(seats: ReadonlyArray<{ x: number; y: number }>): Rect | null {
  if (seats.length === 0) return null
  const xs = seats.map((seat) => seat.x)
  const ys = seats.map((seat) => seat.y)
  const [minX, maxX, minY, maxY] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)]
  const pad = SEAT_SIZE / 2 + SECTION_PADDING
  return { x: minX - pad, y: minY - pad, width: maxX - minX + 2 * pad, height: maxY - minY + 2 * pad }
}

/** Khung của sân khấu / lối đi / nhãn; `null` khi `shape` thiếu kích thước. */
export function elementRect(shape: unknown): (Rect & { rotation: number }) | null {
  const parsed = elementShapeSchema.safeParse(shape)
  if (!parsed.success) return null
  const { x, y, w, h, rotation } = parsed.data
  return { x, y, width: w, height: h, rotation: rotation ?? 0 }
}

export interface View {
  scale: number
  x: number
  y: number
}

export const MIN_SCALE = 0.4
export const MAX_SCALE = 4

/** Khung nhìn ban đầu: vừa khít chiều rộng khung chứa, không phóng to quá 1.5 lần. */
export function fitView(container: { width: number; height: number }, map: { width: number; height: number }): View {
  const scale = Math.min(container.width / map.width, container.height / map.height, 1.5)
  return {
    scale,
    x: (container.width - map.width * scale) / 2,
    y: (container.height - map.height * scale) / 2,
  }
}

/** Khung nhìn đưa `rect` vào giữa và vừa khít khung chứa (giới hạn {@link MAX_SCALE}): chạm vào khu ở màn hẹp. */
export function viewForRect(container: { width: number; height: number }, rect: Rect): View {
  const scale = Math.min(MAX_SCALE, container.width / rect.width, container.height / rect.height)
  return {
    scale,
    x: container.width / 2 - (rect.x + rect.width / 2) * scale,
    y: container.height / 2 - (rect.y + rect.height / 2) * scale,
  }
}

/** Phóng / thu quanh một điểm trên màn hình để điểm đó đứng yên. */
export function zoomAt(view: View, factor: number, point: { x: number; y: number }): View {
  const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, view.scale * factor))
  const ratio = scale / view.scale
  return { scale, x: point.x - (point.x - view.x) * ratio, y: point.y - (point.y - view.y) * ratio }
}
