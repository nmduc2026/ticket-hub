import { Copy, Layers, MapPin, Plus, RotateCcw, RotateCw, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

import {
  type EditorState,
  type ElementType,
  type GridSection,
  rotateBy,
  type Selection,
  seatCountOf,
  totalSeats,
  updateElement,
  updateSection,
  renameSection,
} from './editorModel'

const ELEMENT_TYPES: ElementType[] = ['STAGE', 'AISLE', 'LABEL', 'AREA']

/** Ô số giữ chữ gõ dở, chỉ chốt khi rời ô hoặc Enter và giá trị hợp lệ (mỗi lần chốt là một bước undo). */
function NumberField({ label, value, min, max, disabled, onCommit }: { label: string; value: number; min?: number; max?: number; disabled?: boolean; onCommit: (value: number) => void }) {
  const [draft, setDraft] = useState(String(value))
  useEffect(() => setDraft(String(value)), [value])

  const commit = () => {
    const parsed = Number(draft)
    const valid = draft.trim() !== '' && Number.isFinite(parsed) && (min === undefined || parsed >= min) && (max === undefined || parsed <= max)
    if (valid && parsed !== value) onCommit(parsed)
    else setDraft(String(value))
  }

  return (
    <div className="flex flex-col gap-1">
      <Label className="text-xs">{label}</Label>
      <Input
        inputMode="decimal"
        className="h-8"
        value={draft}
        disabled={disabled}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => event.key === 'Enter' && event.currentTarget.blur()}
        aria-label={label}
      />
    </div>
  )
}

function TextField({ label, value, disabled, onCommit }: { label: string; value: string; disabled?: boolean; onCommit: (value: string) => void }) {
  const [draft, setDraft] = useState(value)
  useEffect(() => setDraft(value), [value])
  const commit = () => (draft.trim() !== '' || value === '' ? draft !== value && onCommit(draft) : setDraft(value))
  return (
    <div className="flex flex-col gap-1">
      <Label className="text-xs">{label}</Label>
      <Input className="h-8" value={draft} disabled={disabled} onChange={(event) => setDraft(event.target.value)} onBlur={commit} onKeyDown={(event) => event.key === 'Enter' && event.currentTarget.blur()} aria-label={label} />
    </div>
  )
}

interface LayersProps {
  state: EditorState
  selection: Selection
  editable: boolean
  onSelect: (selection: Selection) => void
  onAddSection: () => void
  onAddElement: (type: ElementType) => void
}

/** Cột trái "Lớp": danh sách khu và phần tử, thêm mới, tổng số ghế bán. */
export function LayersPanel({ state, selection, editable, onSelect, onAddSection, onAddElement }: LayersProps) {
  const { t } = useTranslation()
  const isSelected = (kind: 'section' | 'element', key: string) => selection?.kind === kind && selection.key === key
  return (
    <aside className="flex flex-col gap-3 rounded-lg border p-3" aria-label={t('adminSeatMap.layers')}>
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-1.5 text-sm font-semibold">
          <Layers className="size-4" aria-hidden="true" />
          {t('adminSeatMap.layers')}
        </h2>
        {editable && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button size="sm" variant="outline">
                <Plus aria-hidden="true" />
                {t('adminSeatMap.add')}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={onAddSection}>{t('adminSeatMap.addSection')}</DropdownMenuItem>
              {ELEMENT_TYPES.map((type) => (
                <DropdownMenuItem key={type} onSelect={() => onAddElement(type)}>
                  {t(`adminSeatMap.element.${type}`)}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>
      <ul className="flex max-h-96 flex-col gap-1 overflow-auto">
        {state.sections.map((section) => (
          <li key={section.key}>
            <button
              type="button"
              onClick={() => onSelect({ kind: 'section', key: section.key })}
              aria-pressed={isSelected('section', section.key)}
              className={cn('hover:bg-muted flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left text-sm', isSelected('section', section.key) && 'bg-muted font-medium')}
            >
              <span className="truncate">{section.name}</span>
              <span className="text-muted-foreground text-xs whitespace-nowrap tabular-nums">{t('adminSeatMap.seatCount', { count: seatCountOf(section) })}</span>
            </button>
          </li>
        ))}
        {state.elements.map((element) => (
          <li key={element.key}>
            <button
              type="button"
              onClick={() => onSelect({ kind: 'element', key: element.key })}
              aria-pressed={isSelected('element', element.key)}
              className={cn('hover:bg-muted flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left text-sm', isSelected('element', element.key) && 'bg-muted font-medium')}
            >
              <span className="truncate">{element.label || t(`adminSeatMap.element.${element.type}`)}</span>
              <span className="text-muted-foreground text-xs">{t('adminSeatMap.notForSale')}</span>
            </button>
          </li>
        ))}
        {state.sections.length === 0 && state.elements.length === 0 && <li className="text-muted-foreground px-2 py-1.5 text-sm">{t('adminSeatMap.emptyLayers')}</li>}
      </ul>
      <p className="text-muted-foreground border-t pt-2 text-xs">{t('adminSeatMap.totalSeats', { count: totalSeats(state) })}</p>
    </aside>
  )
}

interface PropertiesProps {
  state: EditorState
  selection: Selection
  editable: boolean
  hideMode: boolean
  onChange: (next: EditorState) => void
  onToggleHideMode: () => void
  onDuplicate: () => void
  onRemove: () => void
}

/** Cột phải "Thuộc tính": theo thứ được chọn (khu, phần tử, hoặc cả sơ đồ). */
export function PropertiesPanel({ state, selection, editable, hideMode, onChange, onToggleHideMode, onDuplicate, onRemove }: PropertiesProps) {
  const { t } = useTranslation()
  const [confirming, setConfirming] = useState(false)
  useEffect(() => setConfirming(false), [selection?.key])

  const section = selection?.kind === 'section' ? state.sections.find((item) => item.key === selection.key) : undefined
  const element = selection?.kind === 'element' ? state.elements.find((item) => item.key === selection.key) : undefined
  const disabled = !editable

  const removeButton = (
    <Button
      variant={confirming ? 'destructive' : 'outline'}
      size="sm"
      disabled={disabled}
      onClick={() => (confirming ? onRemove() : setConfirming(true))}
      onBlur={() => setConfirming(false)}
    >
      <Trash2 aria-hidden="true" />
      {confirming ? t('adminSeatMap.confirmRemove') : section ? t('adminSeatMap.removeSection') : t('adminSeatMap.removeElement')}
    </Button>
  )

  const grid = section?.kind === 'GRID' ? section : undefined
  const patchGrid = (patch: Partial<Omit<GridSection, 'kind' | 'key'>>) => grid && onChange(updateSection(state, grid.key, patch))

  return (
    <aside className="flex flex-col gap-3 rounded-lg border p-3" aria-label={t('adminSeatMap.properties')}>
      <h2 className="text-sm font-semibold">{t('adminSeatMap.properties')}</h2>

      {!section && !element && (
        <>
          <TextField label={t('adminSeatMap.mapName')} value={state.name} disabled={disabled} onCommit={(name) => onChange({ ...state, name })} />
          <div className="grid grid-cols-2 gap-2">
            <NumberField label={t('adminSeatMap.mapWidth')} value={state.width} min={100} max={20000} disabled={disabled} onCommit={(width) => onChange({ ...state, width })} />
            <NumberField label={t('adminSeatMap.mapHeight')} value={state.height} min={100} max={20000} disabled={disabled} onCommit={(height) => onChange({ ...state, height })} />
          </div>
          <p className="text-muted-foreground text-xs">{t('adminSeatMap.snapHint')}</p>
        </>
      )}

      {section && (
        <>
          <TextField label={t('adminSeatMap.sectionName')} value={section.name} disabled={disabled} onCommit={(name) => onChange(renameSection(state, section.key, name))} />
          {grid ? (
            <>
              <TextField label={t('adminSeatMap.sectionCode')} value={grid.code} disabled={disabled || !grid.key.startsWith('new-')} onCommit={(code) => patchGrid({ code })} />
              <div className="grid grid-cols-2 gap-2">
                <NumberField label={t('adminSeatMap.rows')} value={grid.rows} min={1} max={200} disabled={disabled} onCommit={(rows) => patchGrid({ rows })} />
                <NumberField label={t('adminSeatMap.seatsPerRow')} value={grid.seatsPerRow} min={1} max={500} disabled={disabled} onCommit={(seatsPerRow) => patchGrid({ seatsPerRow })} />
                <NumberField label={t('adminSeatMap.seatGap')} value={grid.seatGap} min={20} max={200} disabled={disabled} onCommit={(seatGap) => patchGrid({ seatGap })} />
                <NumberField label={t('adminSeatMap.rowGap')} value={grid.rowGap} min={20} max={200} disabled={disabled} onCommit={(rowGap) => patchGrid({ rowGap })} />
                <NumberField label={t('adminSeatMap.curve')} value={grid.curve} min={-200} max={200} disabled={disabled} onCommit={(curve) => patchGrid({ curve })} />
                <div className="flex flex-col gap-1">
                  <Label className="text-xs">{t('adminSeatMap.numbering')}</Label>
                  <Button variant="outline" size="sm" disabled={disabled} onClick={() => patchGrid({ numbering: grid.numbering === 'LTR' ? 'RTL' : 'LTR' })}>
                    {grid.numbering === 'LTR' ? t('adminSeatMap.numberingLtr') : t('adminSeatMap.numberingRtl')}
                  </Button>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <NumberField label="X" value={grid.x} disabled={disabled} onCommit={(x) => patchGrid({ x })} />
                <NumberField label="Y" value={grid.y} disabled={disabled} onCommit={(y) => patchGrid({ y })} />
              </div>
              <RotationField value={grid.rotation} disabled={disabled} onCommit={(rotation) => patchGrid({ rotation })} />
              <div className="flex flex-col gap-2">
                <Label className="text-xs">{t('adminSeatMap.hiddenSeats', { count: grid.hiddenSeats.length })}</Label>
                {grid.hiddenSeats.length > 0 && (
                  <ul className="flex flex-wrap gap-1">
                    {grid.hiddenSeats.map((seat) => (
                      <li key={seat}>
                        <button
                          type="button"
                          disabled={disabled}
                          className="bg-muted hover:bg-secondary rounded px-1.5 py-0.5 font-mono text-xs"
                          aria-label={t('adminSeatMap.showSeat', { seat })}
                          onClick={() => patchGrid({ hiddenSeats: grid.hiddenSeats.filter((hidden) => hidden !== seat) })}
                        >
                          {seat} ✕
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <Button variant={hideMode ? 'default' : 'outline'} size="sm" disabled={disabled} aria-pressed={hideMode} onClick={onToggleHideMode}>
                  <MapPin aria-hidden="true" />
                  {t('adminSeatMap.pickOnMap')}
                </Button>
              </div>
              <Button variant="outline" size="sm" disabled={disabled} onClick={onDuplicate}>
                <Copy aria-hidden="true" />
                {t('adminSeatMap.duplicate')}
              </Button>
            </>
          ) : (
            <p className="text-muted-foreground text-xs">{t('adminSeatMap.importedHint', { count: seatCountOf(section) })}</p>
          )}
          {removeButton}
        </>
      )}

      {element && (
        <>
          <TextField label={t('adminSeatMap.elementLabel')} value={element.label} disabled={disabled} onCommit={(label) => onChange(updateElement(state, element.key, { label }))} />
          <div className="grid grid-cols-2 gap-2">
            <NumberField label="X" value={element.x} disabled={disabled} onCommit={(x) => onChange(updateElement(state, element.key, { x }))} />
            <NumberField label="Y" value={element.y} disabled={disabled} onCommit={(y) => onChange(updateElement(state, element.key, { y }))} />
            <NumberField label={t('adminSeatMap.width')} value={element.w} min={10} disabled={disabled} onCommit={(w) => onChange(updateElement(state, element.key, { w }))} />
            <NumberField label={t('adminSeatMap.height')} value={element.h} min={10} disabled={disabled} onCommit={(h) => onChange(updateElement(state, element.key, { h }))} />
          </div>
          <RotationField value={element.rotation} disabled={disabled} onCommit={(rotation) => onChange(updateElement(state, element.key, { rotation }))} />
          {removeButton}
        </>
      )}
    </aside>
  )
}

/** Góc xoay: nhập số hoặc bấm ±15° (ADR 0030). */
function RotationField({ value, disabled, onCommit }: { value: number; disabled?: boolean; onCommit: (value: number) => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex items-end gap-2">
      <div className="flex-1">
        <NumberField label={t('adminSeatMap.rotation')} value={value} min={0} max={359} disabled={disabled} onCommit={onCommit} />
      </div>
      <Button variant="outline" size="icon-sm" disabled={disabled} aria-label={t('adminSeatMap.rotateLeft')} onClick={() => onCommit(rotateBy(value, -1))}>
        <RotateCcw aria-hidden="true" />
      </Button>
      <Button variant="outline" size="icon-sm" disabled={disabled} aria-label={t('adminSeatMap.rotateRight')} onClick={() => onCommit(rotateBy(value, 1))}>
        <RotateCw aria-hidden="true" />
      </Button>
    </div>
  )
}
