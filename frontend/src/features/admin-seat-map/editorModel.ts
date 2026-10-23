import { z } from 'zod'

import type { SeatMap, SeatMapRequest } from '@/api/generated/model'
import { type GridParams, generateSeats } from '@/features/seat-map/generateSeats'
import type { SeatMapSnapshot } from '@/features/seat-map'

/**
 * Mô hình trạng thái của editor sơ đồ ghế (ADR 0030): hàm thuần trên một bản sao của sơ đồ, không đụng React hay konva,
 * nên test được và undo / redo chỉ là giữ các bản sao. Khu `GRID` lưu tham số lưới (ghế sinh bằng `generateSeats`); khu
 * `IMPORTED` (từ Excel) chỉ đổi được tên vì toạ độ ghế nằm sẵn trong DB và API chưa trả ra cho editor.
 */

export const GRID_SNAP = 10
export const ROTATION_STEP = 15

export type ElementType = 'STAGE' | 'AISLE' | 'LABEL' | 'AREA'

export interface GridSection extends GridParams {
  kind: 'GRID'
  key: string
  code: string
  name: string
}

export interface ImportedSection {
  kind: 'IMPORTED'
  key: string
  code: string
  name: string
  seatCount: number
  /** Nguyên văn `shape` từ server, gửi lại khi lưu. */
  shape: Record<string, unknown>
}

export type EditorSection = GridSection | ImportedSection

export interface EditorElement {
  key: string
  type: ElementType
  label: string
  x: number
  y: number
  w: number
  h: number
  rotation: number
}

export interface EditorState {
  name: string
  width: number
  height: number
  sections: EditorSection[]
  elements: EditorElement[]
}

export type Selection = { kind: 'section'; key: string } | { kind: 'element'; key: string } | undefined

const gridShapeSchema = z.object({
  kind: z.literal('GRID'),
  x: z.number(),
  y: z.number(),
  rotation: z.number().default(0),
  rows: z.number().int().positive(),
  seatsPerRow: z.number().int().positive(),
  rowGap: z.number().positive(),
  seatGap: z.number().positive(),
  curve: z.number().default(0),
  numbering: z.enum(['LTR', 'RTL']).default('LTR'),
  hiddenSeats: z.array(z.string()).default([]),
})

const elementShapeSchema = z.object({
  x: z.number(),
  y: z.number(),
  w: z.number(),
  h: z.number(),
  rotation: z.number().default(0),
})

const ELEMENT_TYPES: readonly ElementType[] = ['STAGE', 'AISLE', 'LABEL', 'AREA']

let counter = 0
/** Khoá tạm cho khu / phần tử mới tạo ở client, chưa có id từ server. */
export function newKey(): string {
  counter += 1
  return `new-${counter}`
}

/** Sơ đồ từ API sang trạng thái editor; khu hoặc phần tử có `shape` hỏng bị bỏ qua thay vì làm hỏng cả màn. */
export function fromApi(map: SeatMap): EditorState {
  const sections: EditorSection[] = []
  for (const section of map.sections) {
    const parsed = gridShapeSchema.safeParse(section.shape)
    if (parsed.success) {
      sections.push({ ...parsed.data, key: section.id, code: section.code, name: section.name })
    }
    else if (section.shape.kind === 'IMPORTED') {
      sections.push({ kind: 'IMPORTED', key: section.id, code: section.code, name: section.name, seatCount: section.seatCount, shape: section.shape })
    }
  }
  const elements: EditorElement[] = []
  for (const element of map.elements) {
    const parsed = elementShapeSchema.safeParse(element.shape)
    const type = ELEMENT_TYPES.find((candidate) => candidate === element.type)
    if (parsed.success && type) elements.push({ ...parsed.data, key: element.id, type, label: element.label ?? '' })
  }
  return { name: map.name, width: map.width, height: map.height, sections, elements }
}

/** Trạng thái editor sang body `PUT /admin/seat-maps/{id}`. */
export function toRequest(state: EditorState): SeatMapRequest {
  return {
    name: state.name,
    width: state.width,
    height: state.height,
    sections: state.sections.map((section) => {
      if (section.kind === 'IMPORTED') return { code: section.code, name: section.name, shape: section.shape }
      const { key: _key, code, name, ...shape } = section
      return { code, name, shape: { ...shape } }
    }),
    elements: state.elements.map((element) => ({
      type: element.type,
      label: element.label,
      shape: { x: element.x, y: element.y, w: element.w, h: element.h, rotation: element.rotation },
    })),
  }
}

export function snap(value: number, grid: number = GRID_SNAP): number {
  return Math.round(value / grid) * grid
}

/** Xoay theo bước cố định, kết quả trong [0, 360). */
export function rotateBy(rotation: number, direction: 1 | -1, step: number = ROTATION_STEP): number {
  return (((rotation + direction * step) % 360) + 360) % 360
}

export function seatCountOf(section: EditorSection): number {
  return section.kind === 'IMPORTED' ? section.seatCount : section.rows * section.seatsPerRow - section.hiddenSeats.length
}

export function totalSeats(state: EditorState): number {
  return state.sections.reduce((sum, section) => sum + seatCountOf(section), 0)
}

function mapSection(state: EditorState, key: string, change: (section: GridSection) => GridSection): EditorState {
  return { ...state, sections: state.sections.map((section) => (section.key === key && section.kind === 'GRID' ? change(section) : section)) }
}

function mapElement(state: EditorState, key: string, change: (element: EditorElement) => EditorElement): EditorState {
  return { ...state, elements: state.elements.map((element) => (element.key === key ? change(element) : element)) }
}

export function updateSection(state: EditorState, key: string, patch: Partial<Omit<GridSection, 'kind' | 'key'>>): EditorState {
  return mapSection(state, key, (section) => ({ ...section, ...patch }))
}

/** Đổi tên khu, kể cả khu `IMPORTED` (thứ duy nhất sửa được ở khu đó). */
export function renameSection(state: EditorState, key: string, name: string): EditorState {
  return { ...state, sections: state.sections.map((section) => (section.key === key ? { ...section, name } : section)) }
}

export function moveSection(state: EditorState, key: string, x: number, y: number): EditorState {
  return mapSection(state, key, (section) => ({ ...section, x, y }))
}

export function moveElement(state: EditorState, key: string, x: number, y: number): EditorState {
  return mapElement(state, key, (element) => ({ ...element, x, y }))
}

export function updateElement(state: EditorState, key: string, patch: Partial<Omit<EditorElement, 'key' | 'type'>>): EditorState {
  return mapElement(state, key, (element) => ({ ...element, ...patch }))
}

/** Mã khu chưa dùng, dạng `K1`, `K2`, … (khu mã tối đa 30 ký tự). */
export function nextSectionCode(state: EditorState): string {
  const used = new Set(state.sections.map((section) => section.code))
  for (let index = 1; ; index++) {
    if (!used.has(`K${index}`)) return `K${index}`
  }
}

export function addSection(state: EditorState, key: string = newKey()): { state: EditorState; key: string } {
  const code = nextSectionCode(state)
  const section: GridSection = {
    kind: 'GRID',
    key,
    code,
    name: `Khu ${code}`,
    x: snap(state.width / 2 - 100),
    y: snap(state.height / 2),
    rotation: 0,
    rows: 5,
    seatsPerRow: 10,
    rowGap: 28,
    seatGap: 28,
    curve: 0,
    numbering: 'LTR',
    hiddenSeats: [],
  }
  return { state: { ...state, sections: [...state.sections, section] }, key }
}

/** Nhân bản khu `GRID`: mã mới, tên thêm "(bản sao)", dịch xuống phải một chút để thấy ngay. */
export function duplicateSection(state: EditorState, key: string, copyKey: string = newKey()): { state: EditorState; key: string } | undefined {
  const source = state.sections.find((section) => section.key === key)
  if (!source || source.kind !== 'GRID') return undefined
  const code = nextSectionCode(state)
  const copy: GridSection = { ...source, key: copyKey, code, name: `${source.name} (bản sao)`, x: source.x + 40, y: source.y + 40, hiddenSeats: [...source.hiddenSeats] }
  return { state: { ...state, sections: [...state.sections, copy] }, key: copyKey }
}

export function removeSection(state: EditorState, key: string): EditorState {
  return { ...state, sections: state.sections.filter((section) => section.key !== key) }
}

/** Ẩn / hiện một ghế (vd. `A2`) của khu `GRID`; ghế ẩn không bán và không có toạ độ. */
export function toggleHiddenSeat(state: EditorState, key: string, seat: string): EditorState {
  return mapSection(state, key, (section) => ({
    ...section,
    hiddenSeats: section.hiddenSeats.includes(seat) ? section.hiddenSeats.filter((hidden) => hidden !== seat) : [...section.hiddenSeats, seat],
  }))
}

const ELEMENT_DEFAULTS: Record<ElementType, { label: string; w: number; h: number }> = {
  STAGE: { label: 'Sân khấu', w: 400, h: 80 },
  AISLE: { label: '', w: 40, h: 300 },
  LABEL: { label: 'Nhãn', w: 120, h: 30 },
  AREA: { label: 'Không bán', w: 200, h: 120 },
}

export function addElement(state: EditorState, type: ElementType, key: string = newKey()): { state: EditorState; key: string } {
  const defaults = ELEMENT_DEFAULTS[type]
  const element: EditorElement = {
    key,
    type,
    label: defaults.label,
    x: snap(state.width / 2 - defaults.w / 2),
    y: snap(type === 'STAGE' ? 40 : state.height / 3),
    w: defaults.w,
    h: defaults.h,
    rotation: 0,
  }
  return { state: { ...state, elements: [...state.elements, element] }, key }
}

export function removeElement(state: EditorState, key: string): EditorState {
  return { ...state, elements: state.elements.filter((element) => element.key !== key) }
}

/** Số ghế sinh ra của khu — dùng để kiểm tra khớp với `seatCountOf`. */
export function generatedSeatCount(section: GridSection): number {
  return generateSeats(section).length
}

/** Lịch sử undo / redo: mỗi lần "chốt" một thao tác là một bản sao, không ghi từng bước kéo. */
export interface History<T> {
  past: T[]
  present: T
  future: T[]
}

const HISTORY_LIMIT = 100

export function createHistory<T>(initial: T): History<T> {
  return { past: [], present: initial, future: [] }
}

export function commit<T>(history: History<T>, next: T): History<T> {
  if (Object.is(next, history.present)) return history
  return { past: [...history.past, history.present].slice(-HISTORY_LIMIT), present: next, future: [] }
}

export function undo<T>(history: History<T>): History<T> {
  const previous = history.past.at(-1)
  if (previous === undefined) return history
  return { past: history.past.slice(0, -1), present: previous, future: [history.present, ...history.future] }
}

export function redo<T>(history: History<T>): History<T> {
  const [next, ...rest] = history.future
  if (next === undefined) return history
  return { past: [...history.past, history.present], present: next, future: rest }
}

/** Dữ liệu cho chế độ "Xem như khách": mọi khu `GRID` gán một hạng vé giả, mọi ghế còn trống. Khu IMPORTED chưa có ghế nên bỏ qua. */
export function toGuestSnapshot(state: EditorState): SeatMapSnapshot {
  const tierId = 'preview'
  const grids = state.sections.filter((section): section is GridSection => section.kind === 'GRID')
  return {
    width: state.width,
    height: state.height,
    tiers: [{ id: tierId, name: 'Hạng vé', priceMinor: 0, currency: 'VND', available: 0 }],
    sections: grids.map((section) => {
      const { key: _key, code, name, ...shape } = section
      return { code, name, shape: { ...shape }, tierId }
    }),
    elements: state.elements.map((element) => ({
      type: element.type,
      label: element.label,
      shape: { x: element.x, y: element.y, w: element.w, h: element.h, rotation: element.rotation },
    })),
    seats: grids.flatMap((section) =>
      generateSeats(section).map((seat) => ({
        id: `${section.code}-${seat.rowLabel}${seat.seatNumber}`,
        sectionCode: section.code,
        row: seat.rowLabel,
        number: seat.seatNumber,
        x: seat.x,
        y: seat.y,
        status: 'AVAILABLE',
      })),
    ),
  }
}
