import { describe, expect, it } from 'vitest'

import { applyAdmitted, applyClosed, applyStatus, INITIAL_VIEW, progressPercent, type QueueView } from './queueState'

const NOW = 1_000

describe('máy trạng thái phòng chờ (BR-04)', () => {
  it('QUEUED: lấy vị trí, thời gian chờ, ghi nhớ vị trí đầu tiên để tính tiến độ', () => {
    const first = applyStatus(INITIAL_VIEW, { phase: 'QUEUED', position: 200, estimatedWaitSeconds: 10 }, NOW)
    const later = applyStatus(first, { phase: 'QUEUED', position: 50, estimatedWaitSeconds: 3 }, NOW + 5)

    expect(later).toEqual({ kind: 'queued', position: 50, etaSeconds: 3, initialPosition: 200, updatedAt: NOW + 5 })
    expect(progressPercent(later as Extract<QueueView, { kind: 'queued' }>)).toBe(75)
  })

  it('LOTTERY: chưa có vị trí; tới giờ mở bán chuyển thành QUEUED', () => {
    const lottery = applyStatus(INITIAL_VIEW, { phase: 'LOTTERY' }, NOW)
    expect(lottery).toEqual({ kind: 'lottery' })

    const queued = applyStatus(lottery, { phase: 'QUEUED', position: 7, estimatedWaitSeconds: 1 }, NOW)
    expect(queued.kind).toBe('queued')
  })

  it('ADMITTED: có hạn lượt vào; sự kiện admitted của SSE cũng dẫn tới đó', () => {
    expect(applyStatus(INITIAL_VIEW, { phase: 'ADMITTED', admissionExpiresAt: '2026-12-01T03:15:00Z' }, NOW)).toEqual({
      kind: 'admitted',
      expiresAt: '2026-12-01T03:15:00Z',
    })
    expect(applyAdmitted(INITIAL_VIEW, '2026-12-01T03:15:00Z').kind).toBe('admitted')
  })

  it('đã đóng phòng chờ thì tin muộn hơn không mở lại được', () => {
    const closed = applyClosed('SUSPENDED')

    expect(applyStatus(closed, { phase: 'QUEUED', position: 3, estimatedWaitSeconds: 1 }, NOW)).toBe(closed)
    expect(applyAdmitted(closed, '2026-12-01T03:15:00Z')).toBe(closed)
  })

  it('QUEUED nhưng thiếu vị trí: giữ nguyên trạng thái hiện tại', () => {
    const queued = applyStatus(INITIAL_VIEW, { phase: 'QUEUED', position: 4, estimatedWaitSeconds: 1 }, NOW)

    expect(applyStatus(queued, { phase: 'QUEUED' }, NOW + 1)).toBe(queued)
  })
})
