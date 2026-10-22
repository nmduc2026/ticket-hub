import { z } from 'zod'

/** Bộ lọc đơn hàng toàn hệ thống của Admin (màn #29) ghi lên URL. */
export const adminOrderSearchSchema = z.object({
  status: z.array(z.string()).optional(),
  organizerId: z.string().optional(),
  needsReconcile: z.boolean().optional(),
  q: z.string().optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

export type AdminOrderSearch = z.infer<typeof adminOrderSearchSchema>

export function parseAdminOrderSearch(raw: Record<string, unknown>): AdminOrderSearch {
  const result = adminOrderSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
