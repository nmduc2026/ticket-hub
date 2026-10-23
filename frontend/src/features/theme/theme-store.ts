import { create } from 'zustand'

export type ThemeMode = 'system' | 'light' | 'dark'
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'tickethub-theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

interface ThemeState {
  mode: ThemeMode
  resolved: ResolvedTheme
  setMode: (mode: ThemeMode) => void
  /** Đổi sang chế độ ngược với giao diện đang hiển thị (nút mặt trăng / mặt trời ở topbar). */
  toggle: () => void
}

function readStoredMode(): ThemeMode {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
  } catch {
    // Trình duyệt chặn lưu trữ (chế độ riêng tư): vẫn dùng được, chỉ không nhớ lựa chọn.
    return 'system'
  }
}

function systemPrefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches
}

function resolve(mode: ThemeMode): ResolvedTheme {
  return mode === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : mode
}

function applyToDocument(resolved: ResolvedTheme): void {
  document.documentElement.classList.toggle('dark', resolved === 'dark')
  document.documentElement.style.colorScheme = resolved
}

export const useThemeStore = create<ThemeState>((set, get) => {
  const initialMode = readStoredMode()
  const initialResolved = resolve(initialMode)
  applyToDocument(initialResolved)

  const apply = (mode: ThemeMode) => {
    const resolved = resolve(mode)
    applyToDocument(resolved)
    set({ mode, resolved })
  }

  if (typeof window.matchMedia === 'function') {
    window.matchMedia(DARK_QUERY).addEventListener('change', () => {
      if (get().mode === 'system') {
        apply('system')
      }
    })
  }

  return {
    mode: initialMode,
    resolved: initialResolved,
    setMode: (mode) => {
      try {
        window.localStorage.setItem(STORAGE_KEY, mode)
      } catch {
        // Không lưu được thì lựa chọn chỉ có hiệu lực trong phiên này.
      }
      apply(mode)
    },
    toggle: () => get().setMode(get().resolved === 'dark' ? 'light' : 'dark'),
  }
})
