import { z } from 'zod'

/** Bộ lọc danh sách sự kiện ghi lên URL để chia sẻ link và tải lại trang không mất bộ lọc. */
export const eventSearchSchema = z.object({
  q: z.string().optional(),
  city: z.string().optional(),
  category: z.string().optional(),
  when: z.enum(['all', 'today', 'weekend', 'month']).optional(),
  page: z.number().int().min(0).optional(),
})

export type EventSearchParams = z.infer<typeof eventSearchSchema>

/** Giá trị không hợp lệ trên URL (gõ tay) bị bỏ qua thay vì làm hỏng trang. */
export function parseEventSearch(raw: Record<string, unknown>): EventSearchParams {
  const result = eventSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
