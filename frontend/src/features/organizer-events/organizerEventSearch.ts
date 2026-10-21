import { z } from 'zod'

import { ListOrganizerEventsStatusItem } from '@/api/generated/model'

/** Bộ lọc danh sách sự kiện của organizer ghi lên URL: tải lại trang hoặc chia sẻ link không mất bộ lọc (AC-12). */
export const organizerEventSearchSchema = z.object({
  q: z.string().optional(),
  status: z.array(z.enum(Object.values(ListOrganizerEventsStatusItem) as [string, ...string[]])).optional(),
  venue: z.string().optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

export type OrganizerEventSearch = z.infer<typeof organizerEventSearchSchema>

/** Giá trị không hợp lệ trên URL (gõ tay) bị bỏ qua thay vì làm hỏng trang. */
export function parseOrganizerEventSearch(raw: Record<string, unknown>): OrganizerEventSearch {
  const result = organizerEventSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
