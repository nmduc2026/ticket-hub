import { Columns3, Download, Search } from 'lucide-react'
import { type ReactNode, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

const SEARCH_DEBOUNCE_MS = 300

export interface ColumnToggle {
  id: string
  label: string
}

interface Props {
  search: { value: string; onChange: (value: string) => void; placeholder: string }
  /** Các bộ lọc facet (`FacetFilter`). */
  filters?: ReactNode
  hasFilters: boolean
  onReset: () => void
  columns?: { items: ColumnToggle[]; hidden: ReadonlySet<string>; onToggle: (id: string) => void }
  /** Số dòng đang chọn: "Xuất" thành "Xuất (n)" và `bulkActions` hiện cạnh nhóm phải. */
  selectedCount: number
  onExport?: () => void
  bulkActions?: ReactNode
  /** Nút Tạo, luôn ở hàng đầu bên phải. */
  primaryAction?: ReactNode
}

/**
 * Toolbar dùng chung cho mọi màn danh sách (docs/design/README.md §3), ghim dưới topbar (h-14) khi cuộn.
 * Trái: tìm + bộ lọc + Đặt lại, được xuống dòng. Phải: Cột · Xuất · nút Tạo. Chọn dòng không thêm hàng mới.
 */
export function ListToolbar({
  search,
  filters,
  hasFilters,
  onReset,
  columns,
  selectedCount,
  onExport,
  bulkActions,
  primaryAction,
}: Props) {
  const { t } = useTranslation()

  return (
    <div
      role="toolbar"
      aria-label={t('list.toolbar')}
      data-testid="list-toolbar"
      className="bg-background sticky top-14 z-10 flex flex-wrap items-start justify-between gap-2 py-2"
    >
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
        <SearchField {...search} />
        {filters}
        {hasFilters && (
          <Button variant="ghost" size="sm" onClick={onReset}>
            {t('list.reset')}
          </Button>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2">
        {bulkActions}
        {columns && <ColumnsMenu {...columns} />}
        {onExport && (
          <Button variant="outline" size="sm" onClick={onExport}>
            <Download aria-hidden="true" />
            {selectedCount > 0 ? t('list.exportSelected', { count: selectedCount }) : t('list.export')}
          </Button>
        )}
        {primaryAction}
      </div>
    </div>
  )
}

/** Ô tìm giữ giá trị gõ dở; đẩy lên URL sau một nhịp ngắn để mỗi phím không tạo một lần tải lại. */
function SearchField({ value, onChange, placeholder }: Props['search']) {
  const [draft, setDraft] = useState(value)

  // URL đổi từ ngoài (Đặt lại, nút Back) thì ô tìm theo.
  useEffect(() => setDraft(value), [value])

  useEffect(() => {
    if (draft === value) return
    const timer = setTimeout(() => onChange(draft), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [draft, value, onChange])

  return (
    <div className="relative w-64 max-w-full">
      <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2" aria-hidden="true" />
      <Input
        type="search"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="h-8 pl-8"
      />
    </div>
  )
}

function ColumnsMenu({ items, hidden, onToggle }: NonNullable<Props['columns']>) {
  const { t } = useTranslation()
  return (
    <Popover>
      <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <PopoverTrigger asChild>
            <Button variant="outline" size="icon-sm" aria-label={t('list.columns')}>
              <Columns3 aria-hidden="true" />
            </Button>
          </PopoverTrigger>
        </TooltipTrigger>
        <TooltipContent>{t('list.columns')}</TooltipContent>
      </Tooltip>
      </TooltipProvider>
      <PopoverContent align="end" className="w-52 p-2">
        {items.map((item) => (
          <label key={item.id} className="hover:bg-muted flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm">
            <Checkbox checked={!hidden.has(item.id)} onCheckedChange={() => onToggle(item.id)} />
            {item.label}
          </label>
        ))}
      </PopoverContent>
    </Popover>
  )
}
