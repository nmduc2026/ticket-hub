import { z } from 'zod'

/** Kích thước vẽ của một ghế trên sơ đồ (đơn vị toạ độ sơ đồ, cùng hệ với `seats.x, y`). */
export const SEAT_SIZE = 18

const SECTION_PADDING = 14

/**
 * `shape` của khu / phần tử là JSON tự do ở backend (OpenAPI chỉ khai `JsonNode` rỗng), nên đọc bằng schema thay vì
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

/** Phóng / thu quanh một điểm trên màn hình để điểm đó đứng yên. */
export function zoomAt(view: View, factor: number, point: { x: number; y: number }): View {
  const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, view.scale * factor))
  const ratio = scale / view.scale
  return { scale, x: point.x - (point.x - view.x) * ratio, y: point.y - (point.y - view.y) * ratio }
}
