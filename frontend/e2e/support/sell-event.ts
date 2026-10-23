import { expect, type Browser, type Page } from '@playwright/test'

import { login } from './login'

/** Chuẩn bị dữ liệu cho E2E mua vé: organizer tạo sự kiện trên sơ đồ seed, gán hạng vé cho mọi khu, publish và chờ ON_SALE. */
export interface SellingEvent {
  id: string
  title: string
}

async function xsrfHeaders(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies()
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
  return { 'X-Requested-With': 'XMLHttpRequest', 'X-XSRF-TOKEN': token, 'Content-Type': 'application/json' }
}

export async function createSellingEvent(browser: Browser, priceMinor = 800_000): Promise<SellingEvent> {
  const context = await browser.newContext({ baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173' })
  const page = await context.newPage()
  try {
    await login(page, 'organizer', '/organizer')
    const headers = await xsrfHeaders(page)

    const venues = (await (await page.request.get('/api/v1/venues', { headers })).json()) as { id: string }[]
    const venueId = venues[0]?.id
    expect(venueId, 'cần địa điểm seed (SPRING_PROFILES_ACTIVE=seed)').toBeTruthy()
    const maps = (await (await page.request.get(`/api/v1/venues/${venueId}/seat-maps`, { headers })).json()) as {
      id: string
      sections: { id: string }[]
    }[]
    const seatMap = maps[0]

    const title = `E2E mua vé ${Date.now()}`
    const day = 86_400_000
    const created = await page.request.post('/api/v1/organizer/events', {
      headers,
      data: {
        title,
        venueId,
        seatMapId: seatMap.id,
        startsAt: new Date(Date.now() + 30 * day).toISOString(),
        endsAt: new Date(Date.now() + 30 * day + 3 * 3_600_000).toISOString(),
        saleStartsAt: new Date(Date.now() - 60_000).toISOString(),
        maxTicketsPerUser: 6,
      },
    })
    expect(created.status()).toBe(201)
    const { id } = (await created.json()) as { id: string }

    const tier = await page.request.post(`/api/v1/organizer/events/${id}/ticket-tiers`, {
      headers,
      data: { name: 'Thường', priceMinor },
    })
    expect(tier.status()).toBe(201)
    const { id: tierId } = (await tier.json()) as { id: string }

    const assigned = await page.request.put(`/api/v1/organizer/events/${id}/tier-sections`, {
      headers,
      data: { assignments: seatMap.sections.map((section) => ({ sectionId: section.id, tierId })) },
    })
    expect(assigned.ok()).toBe(true)

    const published = await page.request.post(`/api/v1/organizer/events/${id}/publish`, { headers })
    expect(published.ok(), await published.text()).toBe(true)

    // Tồn kho dựng bất đồng bộ sau publish (§7.9): chờ tới khi sự kiện ON_SALE.
    await expect
      .poll(async () => ((await (await page.request.get(`/api/v1/events/${id}`, { headers })).json()) as { status: string }).status, {
        timeout: 30_000,
      })
      .toBe('ON_SALE')
    return { id, title }
  } finally {
    await context.close()
  }
}
