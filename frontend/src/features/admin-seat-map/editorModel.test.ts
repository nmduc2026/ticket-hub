import type { SeatMap } from '@/api/generated/model'

import {
  addElement,
  addSection,
  commit,
  createHistory,
  duplicateSection,
  fromApi,
  generatedSeatCount,
  moveSection,
  redo,
  removeSection,
  renameSection,
  rotateBy,
  seatCountOf,
  snap,
  toggleHiddenSeat,
  toRequest,
  totalSeats,
  undo,
  type GridSection,
} from './editorModel'

const API_MAP: SeatMap = {
  id: 'm1',
  name: 'Khán phòng chính',
  version: 1,
  width: 1000,
  height: 640,
  locked: false,
  sections: [
    {
      id: 's1',
      code: 'A',
      name: 'Khu A',
      seatCount: 29,
      shape: { kind: 'GRID', x: 100, y: 200, rotation: 15, rows: 3, seatsPerRow: 10, rowGap: 28, seatGap: 28, curve: 6, numbering: 'LTR', hiddenSeats: ['A2'] },
    },
    { id: 's2', code: 'X', name: 'Khu nhập', seatCount: 40, shape: { kind: 'IMPORTED' } },
    { id: 's3', code: 'BAD', name: 'Hỏng', seatCount: 0, shape: { kind: 'GRID', rows: 'nhieu' } },
  ],
  elements: [
    { id: 'e1', type: 'STAGE', label: 'Sân khấu', shape: { x: 300, y: 40, w: 400, h: 80, rotation: 0 } },
    { id: 'e2', type: 'KHAC', label: 'Lạ', shape: { x: 0, y: 0, w: 1, h: 1 } },
  ],
}

describe('editorModel', () => {
  it('fromApi đọc khu GRID / IMPORTED và phần tử hợp lệ, bỏ khu và phần tử có shape hỏng', () => {
    const state = fromApi(API_MAP)
    expect(state.sections.map((section) => `${section.kind}:${section.code}`)).toEqual(['GRID:A', 'IMPORTED:X'])
    expect(state.elements.map((element) => element.key)).toEqual(['e1'])
    expect(totalSeats(state)).toBe(29 + 40)
  })

  it('toRequest rồi fromApi trả lại đúng khu và phần tử (không mất tham số)', () => {
    const state = fromApi(API_MAP)
    const request = toRequest(state)
    expect(request.sections[0]).toEqual({ code: 'A', name: 'Khu A', shape: expect.objectContaining({ kind: 'GRID', x: 100, rotation: 15, hiddenSeats: ['A2'] }) })
    expect(request.sections[0].shape).not.toHaveProperty('key')
    expect(request.sections[1]).toEqual({ code: 'X', name: 'Khu nhập', shape: { kind: 'IMPORTED' } })
    expect(request.elements[0]).toEqual({ type: 'STAGE', label: 'Sân khấu', shape: { x: 300, y: 40, w: 400, h: 80, rotation: 0 } })
  })

  it('số ghế của khu khớp số ghế sinh ra từ generateSeats', () => {
    const [grid] = fromApi(API_MAP).sections
    expect(seatCountOf(grid)).toBe(29)
    expect(generatedSeatCount(grid as GridSection)).toBe(29)
  })

  it('snap về lưới 10px và xoay theo bước 15° vòng qua 0 / 360', () => {
    expect(snap(14)).toBe(10)
    expect(snap(15)).toBe(20)
    expect(snap(-4)).toBe(-0)
    expect(rotateBy(0, -1)).toBe(345)
    expect(rotateBy(345, 1)).toBe(0)
    expect(rotateBy(30, 1)).toBe(45)
  })

  it('thêm khu lấy mã chưa dùng; nhân bản đổi mã, dịch vị trí, không chia sẻ mảng ghế ẩn', () => {
    let state = fromApi(API_MAP)
    const added = addSection(state, 'k-a')
    expect(added.state.sections.at(-1)?.code).toBe('K1')
    state = added.state
    const copy = duplicateSection(state, 's1', 'k-b')
    expect(copy).toBeDefined()
    const copied = copy!.state.sections.find((section) => section.key === 'k-b') as GridSection
    expect(copied.code).toBe('K2')
    expect(copied.name).toBe('Khu A (bản sao)')
    expect([copied.x, copied.y]).toEqual([140, 240])
    const hidden = toggleHiddenSeat(copy!.state, 'k-b', 'B3')
    expect((hidden.sections.find((section) => section.key === 's1') as GridSection).hiddenSeats).toEqual(['A2'])
    expect((hidden.sections.find((section) => section.key === 'k-b') as GridSection).hiddenSeats).toEqual(['A2', 'B3'])
  })

  it('khu IMPORTED không nhân bản, không di chuyển được; vẫn đổi tên được', () => {
    const state = fromApi(API_MAP)
    expect(duplicateSection(state, 's2')).toBeUndefined()
    expect(moveSection(state, 's2', 5, 5)).toEqual(state)
    expect(renameSection(state, 's2', 'Tên mới').sections[1].name).toBe('Tên mới')
  })

  it('thêm phần tử đặt sân khấu ở trên cùng, xoá khu bỏ khỏi danh sách', () => {
    const state = fromApi(API_MAP)
    const { state: next, key } = addElement(state, 'STAGE', 'e-new')
    expect(next.elements.find((element) => element.key === key)).toMatchObject({ type: 'STAGE', y: 40, label: 'Sân khấu' })
    expect(removeSection(next, 's1').sections.map((section) => section.key)).toEqual(['s2'])
  })

  it('undo / redo giữ đúng thứ tự; thao tác mới sau undo xoá nhánh redo; không đổi gì thì không thêm lịch sử', () => {
    const base = fromApi(API_MAP)
    let history = createHistory(base)
    const moved = moveSection(base, 's1', 200, 200)
    history = commit(history, moved)
    history = commit(history, moved)
    expect(history.past).toHaveLength(1)

    history = undo(history)
    expect(history.present).toBe(base)
    expect(history.future).toHaveLength(1)
    history = redo(history)
    expect(history.present).toBe(moved)

    history = undo(history)
    history = commit(history, renameSection(base, 's1', 'Khác'))
    expect(history.future).toHaveLength(0)
    expect(redo(history)).toBe(history)
    expect(undo(createHistory(base))).toEqual(createHistory(base))
  })
})
