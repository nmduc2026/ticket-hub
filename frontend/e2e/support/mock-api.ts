import type { Page, Route } from '@playwright/test'

/**
 * Giả lập API ở tầng trình duyệt để chạy E2E không cần compose / Gateway (chỉ cần `npm run dev`).
 * Test dùng `*.mock.spec.ts`; test chạm stack thật vẫn đăng nhập qua Keycloak (support/login.ts).
 */
export const mockUser = {
  id: '00000000-0000-7000-8000-000000000002',
  email: 'organizer@tickethub.local',
  fullName: 'Ban Tổ Chức',
  username: 'organizer',
  roles: ['CUSTOMER', 'ORGANIZER'],
  enabled: true,
}

export function json(route: Route, body: unknown, status = 200): Promise<void> {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

/** Đăng nhập giả: `GET /api/v1/me` trả hồ sơ organizer. */
export async function mockSession(page: Page): Promise<void> {
  await page.route('**/api/v1/me', (route) => json(route, mockUser))
}
