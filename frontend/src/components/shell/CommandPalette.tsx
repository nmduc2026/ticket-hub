import { useNavigate } from '@tanstack/react-router'
import { Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'

import type { NavGroup } from './nav'

/**
 * Tìm nhanh (Ctrl K): popup tìm trang và thao tác. Esc và bấm ra ngoài đều đóng, focus trở về nơi đang đứng trước
 * khi mở. Nhóm "Thao tác" sẽ xuất hiện khi có thao tác thật để gọi; hiện chỉ có "Trang".
 */
export function CommandPalette({ groups }: { groups: NavGroup[] }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  // Nơi đang giữ focus trước khi mở (nút Tìm nhanh hoặc bất cứ đâu khi dùng Ctrl K): đóng xong trả focus về đó.
  const returnFocusTo = useRef<HTMLElement | null>(null)

  const show = () => {
    returnFocusTo.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setOpen(true)
  }

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        if (open) {
          setOpen(false)
        } else {
          show()
        }
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open])

  const items = groups.flatMap((group) => group.items)

  return (
    <>
      <button
        type="button"
        onClick={show}
        aria-haspopup="dialog"
        className="border-input bg-card text-muted-foreground hover:border-ring/50 hidden h-9 min-w-56 items-center gap-2 rounded-lg border px-3 text-sm md:inline-flex"
      >
        <Search className="size-4" aria-hidden="true" />
        <span>{t('shell.quickSearch')}</span>
        <kbd className="bg-muted ml-auto rounded border px-1.5 text-xs">Ctrl K</kbd>
      </button>
      <CommandDialog
        open={open}
        onOpenChange={setOpen}
        title={t('shell.quickSearchTitle')}
        description={t('shell.quickSearchTitle')}
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          returnFocusTo.current?.focus()
        }}
      >
        <CommandInput placeholder={t('shell.quickSearchPlaceholder')} />
        <CommandList>
          <CommandEmpty>{t('shell.quickSearchEmpty')}</CommandEmpty>
          <CommandGroup heading={t('shell.pages')}>
            {items.map((item) => (
              <CommandItem
                key={item.to}
                value={t(item.labelKey)}
                onSelect={() => {
                  setOpen(false)
                  void navigate({ to: item.to })
                }}
              >
                <item.icon />
                {t(item.labelKey)}
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </CommandDialog>
    </>
  )
}
