import { useThemeStore } from './theme-store'

describe('theme store', () => {
  beforeEach(() => {
    window.localStorage.clear()
    useThemeStore.getState().setMode('light')
  })

  it('toggle đổi sang giao diện ngược lại, gắn class dark lên <html> và nhớ lựa chọn', () => {
    useThemeStore.getState().toggle()

    expect(useThemeStore.getState().resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(window.localStorage.getItem('tickethub-theme')).toBe('dark')

    useThemeStore.getState().toggle()

    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(window.localStorage.getItem('tickethub-theme')).toBe('light')
  })

  it('vẫn hoạt động khi trình duyệt chặn localStorage', () => {
    const blocked = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })

    expect(() => useThemeStore.getState().setMode('dark')).not.toThrow()
    expect(useThemeStore.getState().resolved).toBe('dark')

    blocked.mockRestore()
  })
})
