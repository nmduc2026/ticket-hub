import { z } from 'zod'

/** Bộ lọc danh sách người dùng ghi lên URL; `view` là người đang mở ở side sheet. */
export const adminUserSearchSchema = z.object({
  q: z.string().optional(),
  role: z.enum(['CUSTOMER', 'ORGANIZER', 'ADMIN']).optional(),
  view: z.string().optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

export type AdminUserSearch = z.infer<typeof adminUserSearchSchema>

/** Giá trị không hợp lệ trên URL (gõ tay) bị bỏ qua thay vì làm hỏng trang. */
export function parseAdminUserSearch(raw: Record<string, unknown>): AdminUserSearch {
  const result = adminUserSearchSchema.safeParse(raw)
  return result.success ? result.data : {}
}
