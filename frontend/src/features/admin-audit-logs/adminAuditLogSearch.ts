import { z } from 'zod'

import { AdminAuditLogRowAction } from '@/api/generated/model'

/** Bộ lọc nhật ký thao tác quản trị (màn #31) ghi lên URL. */
export const adminAuditLogSearchSchema = z.object({
  q: z.string().optional(),
  actor: z.string().optional(),
  action: z.enum(Object.values(AdminAuditLogRowAction) as [string, ...string[]]).optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

export type AdminAuditLogSearch = z.infer<typeof adminAuditLogSearchSchema>

export function parseAdminAuditLogSearch(raw: Record<string, unknown>): AdminAuditLogSearch {
  const result = adminAuditLogSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
