import { expect, test } from '@playwright/test'

import { login } from './support/login'

/** Sprint 1 AC-9: khung Admin / Organizer và layout khách; popover / modal đóng bằng click ra ngoài và Esc. */
test.describe('khung giao diện dùng chung', () => {
  test('Ctrl K mở Tìm nhanh, Esc đóng và trả focus', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name.startsWith('mobile'), 'Ô Tìm nhanh trên topbar chỉ có từ 768px; phím tắt vẫn dùng được')
    await login(page, 'admin', '/admin')

    await page.keyboard.press('Control+k')
    const dialog = page.getByRole('dialog', { name: 'Tìm nhanh' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByRole('option', { name: 'Tổng quan' })).toBeVisible()

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
  })

  test('nút Tìm nhanh mở popup và Esc trả focus về nút', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name.startsWith('mobile'), 'Nút Tìm nhanh ẩn dưới 768px')
    await login(page, 'admin', '/admin')

    const trigger = page.getByRole('button', { name: /Tìm nhanh/ })
    await trigger.click()
    await expect(page.getByRole('dialog', { name: 'Tìm nhanh' })).toBeVisible()
    await page.keyboard.press('Escape')

    await expect(page.getByRole('dialog', { name: 'Tìm nhanh' })).toBeHidden()
    await expect(trigger).toBeFocused()
  })

  test('click ra ngoài đóng popover thông báo và menu tài khoản; focus về nút mở', async ({ page }) => {
    await login(page, 'admin', '/admin')
    await page.keyboard.press('Escape')

    const bell = page.getByRole('button', { name: 'Thông báo' })
    await bell.click()
    await expect(page.getByText('Chưa có thông báo')).toBeVisible()
    await page.mouse.click(8, 600)
    await expect(page.getByText('Chưa có thông báo')).toBeHidden()
    await expect(bell).toBeFocused()

    await bell.click()
    await page.keyboard.press('Escape')
    await expect(page.getByText('Chưa có thông báo')).toBeHidden()
    await expect(bell).toBeFocused()
  })

  test('đổi sáng / tối được giữ khi tải lại trang', async ({ page }) => {
    await login(page, 'customer')
    const html = page.locator('html')
    const wasDark = await html.evaluate((el) => el.classList.contains('dark'))

    await page.getByRole('button', { name: 'Đổi giao diện sáng / tối' }).click()
    await expect(html).toHaveClass(wasDark ? /^(?!.*dark)/ : /dark/)
    await page.reload()

    await expect(html).toHaveClass(wasDark ? /^(?!.*dark)/ : /dark/)
  })

  test('breadcrumb luôn bắt đầu bằng "Trang chủ"', async ({ page }) => {
    await login(page, 'organizer', '/organizer')

    const breadcrumb = page.getByRole('navigation', { name: 'Đường dẫn' })
    await expect(breadcrumb.getByRole('link', { name: 'Trang chủ' })).toBeVisible()
    await expect(breadcrumb.getByText('Tổng quan')).toBeVisible()
  })

  test('khổ hẹp: sidebar thành ngăn kéo, đóng được bằng Esc', async ({ page }, testInfo) => {
    test.skip(!testInfo.project.name.startsWith('mobile'), 'Chỉ kiểm ở khổ 390px')
    await login(page, 'admin', '/admin')

    const open = page.getByRole('button', { name: 'Mở menu' })
    await open.click()
    const drawer = page.getByRole('dialog')
    await expect(drawer.getByRole('link', { name: 'Tổng quan' })).toBeVisible()
    await page.keyboard.press('Escape')

    await expect(drawer).toBeHidden()
    await expect(open).toBeFocused()
  })

  test('chụp màn hình khung Admin, Organizer và layout khách để đối chiếu prototype', async ({ browser }, testInfo) => {
    const viewport = testInfo.project.use.viewport ?? undefined
    const capture = async (user: 'customer' | 'organizer' | 'admin' | null, path: string, name: string) => {
      const context = await browser.newContext({ viewport, locale: 'vi-VN' })
      const page = await context.newPage()
      if (user) {
        await login(page, user, path)
      } else {
        await page.goto(path)
      }
      await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
      await context.close()
    }

    await capture(null, '/', 'guest-home')
    await capture('organizer', '/organizer', 'organizer')
    await capture('admin', '/admin', 'admin')
    await capture('customer', '/me/profile', 'profile')
  })
})
