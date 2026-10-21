import { z } from 'zod'

import { ListAdminOrganizersStatus } from '@/api/generated/model'

/** Bộ lọc danh sách hồ sơ organizer ghi lên URL; `view` là hồ sơ đang mở ở side sheet. */
export const adminOrganizerSearchSchema = z.object({
  q: z.string().optional(),
  status: z.enum(Object.values(ListAdminOrganizersStatus) as [string, ...string[]]).optional(),
  view: z.string().optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

export type AdminOrganizerSearch = z.infer<typeof adminOrganizerSearchSchema>

/** Giá trị không hợp lệ trên URL (gõ tay) bị bỏ qua thay vì làm hỏng trang. */
export function parseAdminOrganizerSearch(raw: Record<string, unknown>): AdminOrganizerSearch {
  const result = adminOrganizerSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
