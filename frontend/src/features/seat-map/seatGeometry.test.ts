import { elementRect, fitView, MAX_SCALE, MIN_SCALE, SEAT_SIZE, sectionRect, seatsRect, viewForRect, zoomAt } from './seatGeometry'

describe('sectionRect', () => {
  it('khung khu GRID bao quanh mọi ghế của lưới với phần đệm', () => {
    const rect = sectionRect({ kind: 'GRID', x: 60, y: 160, rows: 8, seatsPerRow: 20, rowGap: 26, seatGap: 24 })
    expect(rect).toEqual({
      x: 60 - SEAT_SIZE / 2 - 14,
      y: 160 - SEAT_SIZE / 2 - 14,
      width: 19 * 24 + SEAT_SIZE + 28,
      height: 7 * 26 + SEAT_SIZE + 28,
    })
  })

  it('khu IMPORTED hoặc shape lạ không có khung (không làm hỏng sơ đồ)', () => {
    expect(sectionRect({ kind: 'IMPORTED' })).toBeNull()
    expect(sectionRect(null)).toBeNull()
    expect(sectionRect({})).toBeNull()
  })
})

describe('elementRect', () => {
  it('đọc sân khấu, mặc định không xoay', () => {
    expect(elementRect({ x: 350, y: 40, w: 500, h: 60 })).toEqual({ x: 350, y: 40, width: 500, height: 60, rotation: 0 })
    expect(elementRect({ x: 1 })).toBeNull()
  })
})

describe('khung nhìn', () => {
  it('fitView vừa khít khung chứa và căn giữa, không phóng quá 1.5 lần', () => {
    const view = fitView({ width: 600, height: 400 }, { width: 1200, height: 800 })
    expect(view.scale).toBe(0.5)
    expect(view.x).toBe(0)
    const small = fitView({ width: 3000, height: 3000 }, { width: 1200, height: 800 })
    expect(small.scale).toBe(1.5)
    expect(small.x).toBe((3000 - 1800) / 2)
  })

  it('zoomAt giữ điểm tham chiếu đứng yên', () => {
    const before = { scale: 1, x: 10, y: 20 }
    const point = { x: 200, y: 100 }
    const after = zoomAt(before, 2, point)
    // Toạ độ sơ đồ dưới con trỏ trước và sau phải trùng nhau.
    expect((point.x - before.x) / before.scale).toBeCloseTo((point.x - after.x) / after.scale)
    expect((point.y - before.y) / before.scale).toBeCloseTo((point.y - after.y) / after.scale)
  })

  it('zoomAt chặn trong khoảng cho phép', () => {
    expect(zoomAt({ scale: 1, x: 0, y: 0 }, 100, { x: 0, y: 0 }).scale).toBe(MAX_SCALE)
    expect(zoomAt({ scale: 1, x: 0, y: 0 }, 0.001, { x: 0, y: 0 }).scale).toBe(MIN_SCALE)
  })

  it('viewForRect đưa khu vào giữa và vừa khít khung chứa, không quá MAX_SCALE', () => {
    const rect = { x: 100, y: 200, width: 400, height: 200 }
    const view = viewForRect({ width: 400, height: 400 }, rect)
    expect(view.scale).toBe(1)
    // Tâm khu nằm giữa khung chứa.
    expect((rect.x + rect.width / 2) * view.scale + view.x).toBe(200)
    expect((rect.y + rect.height / 2) * view.scale + view.y).toBe(200)
    expect(viewForRect({ width: 400, height: 400 }, { x: 0, y: 0, width: 10, height: 10 }).scale).toBe(MAX_SCALE)
  })

  it('seatsRect bao mọi ghế kèm đệm (khu IMPORTED); không có ghế thì null', () => {
    const rect = seatsRect([{ x: 10, y: 20 }, { x: 110, y: 70 }])
    expect(rect).not.toBeNull()
    expect(rect?.x).toBeLessThan(10)
    expect((rect?.x ?? 0) + (rect?.width ?? 0)).toBeGreaterThan(110)
    expect(seatsRect([])).toBeNull()
  })
})
