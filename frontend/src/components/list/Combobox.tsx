import { ChevronDown } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Command, CommandEmpty, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

export interface ComboboxOption {
  value: string
  label: string
}

interface Props {
  id?: string
  value: string | undefined
  onChange: (value: string) => void
  options: ComboboxOption[]
  placeholder: string
  disabled?: boolean
  'aria-invalid'?: boolean
}

/**
 * Dropdown có ô tìm ghim đầu danh sách, dùng cho danh sách thực thể (địa điểm, tỉnh / xã…) hoặc trên 7 lựa chọn
 * (docs/design/README.md §3). Popover tự mở lên trên khi gần mép dưới. Không dùng `<select>` của trình duyệt.
 */
export function Combobox({ id, value, onChange, options, placeholder, disabled, ...rest }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const selected = options.find((option) => option.value === value)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-invalid={rest['aria-invalid']}
          disabled={disabled}
          className={cn('w-full justify-between font-normal', !selected && 'text-muted-foreground')}
        >
          <span className="truncate">{selected?.label ?? placeholder}</span>
          <ChevronDown className="size-4 shrink-0 opacity-50" aria-hidden="true" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-(--radix-popover-trigger-width) p-0">
        <Command>
          <CommandInput placeholder={t('list.comboboxSearch')} />
          <CommandList>
            <CommandEmpty>{t('list.searchResultsEmpty')}</CommandEmpty>
            {options.map((option) => (
              <CommandItem
                key={option.value}
                value={option.label}
                data-checked={option.value === value}
                onSelect={() => {
                  onChange(option.value)
                  setOpen(false)
                }}
                className={cn(option.value === value && 'font-semibold')}
              >
                <span className="truncate">{option.label}</span>
              </CommandItem>
            ))}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
