import { startOfVnDay, startOfVnMonth, vnWeekday } from '@/lib/date'

export type EventTimeFilter = 'all' | 'today' | 'weekend' | 'month'

export interface TimeRange {
  from?: string
  to?: string
}

/** Đổi lựa chọn "Thời gian" thành khoảng [from, to) theo ngày giờ Việt Nam, dạng ISO UTC cho API. */
export function timeRangeFor(filter: EventTimeFilter | undefined, now: Date): TimeRange {
  switch (filter) {
    case 'today':
      return { from: startOfVnDay(now), to: startOfVnDay(now, 1) }
    case 'weekend': {
      const weekday = vnWeekday(now)
      if (weekday === 0) {
        return { from: startOfVnDay(now), to: startOfVnDay(now, 1) } // đang là Chủ nhật: chỉ còn hôm nay
      }
      const saturdayOffset = 6 - weekday
      return { from: startOfVnDay(now, saturdayOffset), to: startOfVnDay(now, saturdayOffset + 2) }
    }
    case 'month':
      return { from: startOfVnMonth(now), to: startOfVnMonth(now, 1) }
    default:
      return {}
  }
}
