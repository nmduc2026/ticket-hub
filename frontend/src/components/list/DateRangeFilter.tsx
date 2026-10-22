import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

export interface DateRangeValue {
  from?: string
  to?: string
}

/**
 * Bộ lọc "Tuỳ chọn khoảng ngày" (docs/design/README.md §3): nút viền đứt, mở popover có ô Từ / Đến (`type="date"`).
 * Giá trị nằm ở URL do component cha giữ, cùng cách với {@link import('./FacetFilter').FacetFilter}.
 */
export function DateRangeFilter({ label, value, onChange }: { label: string; value: DateRangeValue; onChange: (value: DateRangeValue) => void }) {
  const { t } = useTranslation()
  const summary = value.from || value.to ? `${value.from ?? '…'} – ${value.to ?? '…'}` : undefined

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className="w-56 justify-start border-dashed font-normal">
          <span className="font-medium">{label}</span>
          {summary && (
            <>
              <span aria-hidden="true" className="bg-border h-4 w-px" />
              <span className="min-w-0 flex-1 truncate text-left">{summary}</span>
            </>
          )}
          {!summary && <span className="flex-1" />}
          <ChevronDown className="text-muted-foreground ml-auto size-4 shrink-0" aria-hidden="true" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="flex w-64 flex-col gap-3 p-3">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="date-range-from">{t('list.dateFrom')}</Label>
          <Input id="date-range-from" type="date" value={value.from ?? ''} onChange={(event) => onChange({ ...value, from: event.target.value || undefined })} className="h-8" />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="date-range-to">{t('list.dateTo')}</Label>
          <Input id="date-range-to" type="date" value={value.to ?? ''} onChange={(event) => onChange({ ...value, to: event.target.value || undefined })} className="h-8" />
        </div>
        {summary && (
          <Button variant="ghost" size="sm" onClick={() => onChange({ from: undefined, to: undefined })}>
            {t('list.reset')}
          </Button>
        )}
      </PopoverContent>
    </Popover>
  )
}
