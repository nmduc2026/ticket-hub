import { ChevronDown } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'

export interface FacetOption {
  value: string
  label: string
}

type FacetProps =
  | { mode: 'multi'; value: string[]; onChange: (value: string[]) => void }
  | { mode: 'single'; value: string | undefined; onChange: (value: string | undefined) => void }

type Props = FacetProps & { label: string; options: FacetOption[] }

const SEARCH_THRESHOLD = 7
const ALL = '__all__'

/**
 * Bộ lọc facet phương án A (docs/design/README.md §3): nút viền đứt rộng cố định, chọn xong hiện vạch ngăn và giá trị
 * (quá 2 giá trị → "N đã chọn"). Chọn nhiều có dòng "Chọn nhiều" chọn / bỏ tất cả (3 trạng thái); chọn một có "Tất cả".
 * Không đếm số lượng theo option. Giá trị nằm ở URL do component cha giữ.
 */
export function FacetFilter({ label, options, ...props }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')

  const selectedLabels = useMemo(() => {
    const selected = props.mode === 'multi' ? props.value : props.value ? [props.value] : []
    return options.filter((option) => selected.includes(option.value)).map((option) => option.label)
  }, [options, props.mode, props.value])

  const summary = selectedLabels.length > 2 ? t('list.selectedCount', { count: selectedLabels.length }) : selectedLabels.join(', ')
  const visible = options.filter((option) => option.label.toLowerCase().includes(query.trim().toLowerCase()))

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) setQuery('')
      }}
    >
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
      <PopoverContent align="start" className="w-64 p-2">
        {options.length > SEARCH_THRESHOLD && (
          <Input
            autoFocus
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t('list.comboboxSearch')}
            aria-label={label}
            className="mb-2 h-8"
          />
        )}
        {props.mode === 'multi' ? (
          <MultiList options={visible} value={props.value} allOptions={options} onChange={props.onChange} />
        ) : (
          <SingleList options={visible} value={props.value} onChange={props.onChange} />
        )}
        {visible.length === 0 && (
          <p className="text-muted-foreground py-4 text-center text-sm">{t('list.searchResultsEmpty')}</p>
        )}
      </PopoverContent>
    </Popover>
  )
}

function MultiList({
  options,
  allOptions,
  value,
  onChange,
}: {
  options: FacetOption[]
  allOptions: FacetOption[]
  value: string[]
  onChange: (value: string[]) => void
}) {
  const { t } = useTranslation()
  const allValues = allOptions.map((option) => option.value)
  const selectedCount = allValues.filter((optionValue) => value.includes(optionValue)).length
  const allState = selectedCount === 0 ? false : selectedCount === allValues.length ? true : 'indeterminate'

  const toggle = (optionValue: string, checked: boolean) =>
    onChange(checked ? [...value, optionValue] : value.filter((existing) => existing !== optionValue))

  return (
    <div className="flex flex-col gap-0.5" role="group" aria-label={t('list.selectMultiple')}>
      <label className="hover:bg-muted flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium">
        <Checkbox checked={allState} onCheckedChange={(checked) => onChange(checked === true ? allValues : [])} />
        {t('list.selectMultiple')}
      </label>
      <div className="bg-border my-1 h-px" />
      {options.map((option) => (
        <label
          key={option.value}
          className="hover:bg-muted flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm"
        >
          <Checkbox
            checked={value.includes(option.value)}
            onCheckedChange={(checked) => toggle(option.value, checked === true)}
          />
          <span className="truncate">{option.label}</span>
        </label>
      ))}
    </div>
  )
}

function SingleList({
  options,
  value,
  onChange,
}: {
  options: FacetOption[]
  value: string | undefined
  onChange: (value: string | undefined) => void
}) {
  const { t } = useTranslation()
  return (
    <RadioGroup value={value ?? ALL} onValueChange={(next) => onChange(next === ALL ? undefined : next)} className="gap-0.5">
      <SingleRow value={ALL} label={t('list.all')} />
      {options.map((option) => (
        <SingleRow key={option.value} value={option.value} label={option.label} />
      ))}
    </RadioGroup>
  )
}

function SingleRow({ value, label }: { value: string; label: string }) {
  const id = `facet-${value}`
  return (
    <div className="hover:bg-muted flex items-center gap-2 rounded-md px-2 py-1.5">
      <RadioGroupItem value={value} id={id} />
      <Label htmlFor={id} className="flex-1 cursor-pointer truncate font-normal">
        {label}
      </Label>
    </div>
  )
}
