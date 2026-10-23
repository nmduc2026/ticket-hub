import { z } from 'zod'

/** Bộ lọc đơn hàng của organizer (màn #14) ghi lên URL, cùng cách với {@link import('../organizer-events/organizerEventSearch').organizerEventSearchSchema}. */
export const organizerOrderSearchSchema = z.object({
  eventId: z.string().optional(),
  status: z.array(z.string()).optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  q: z.string().optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

export type OrganizerOrderSearch = z.infer<typeof organizerOrderSearchSchema>

export function parseOrganizerOrderSearch(raw: Record<string, unknown>): OrganizerOrderSearch {
  const result = organizerOrderSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
