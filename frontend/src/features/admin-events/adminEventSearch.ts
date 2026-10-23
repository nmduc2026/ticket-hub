import { z } from 'zod'

import { AdminEventSummaryStatus } from '@/api/generated/model'

/** Bộ lọc danh sách sự kiện của Admin (màn #34) ghi lên URL. */
export const adminEventSearchSchema = z.object({
  status: z.enum(Object.values(AdminEventSummaryStatus) as [string, ...string[]]).optional(),
  organizerId: z.string().optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  q: z.string().optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

export type AdminEventSearch = z.infer<typeof adminEventSearchSchema>

export function parseAdminEventSearch(raw: Record<string, unknown>): AdminEventSearch {
  const result = adminEventSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
