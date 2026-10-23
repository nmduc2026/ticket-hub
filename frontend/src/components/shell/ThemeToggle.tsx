import { Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { useThemeStore } from '@/features/theme/theme-store'

export function ThemeToggle() {
  const { t } = useTranslation()
  const resolved = useThemeStore((state) => state.resolved)
  const toggle = useThemeStore((state) => state.toggle)

  return (
    <Button variant="ghost" size="icon" aria-label={t('theme.toggle')} onClick={toggle}>
      {resolved === 'dark' ? <Sun /> : <Moon />}
    </Button>
  )
}
