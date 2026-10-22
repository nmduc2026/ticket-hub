import { Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ArrowLeft, Eye, Maximize, Redo2, Undo2 } from 'lucide-react'
import { type KeyboardEvent, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getGetAdminSeatMapQueryKey, useCopyAdminSeatMap, useGetAdminSeatMap, useUpdateAdminSeatMap } from '@/api/generated/endpoints/admin-seat-map-controller/admin-seat-map-controller'
import type { SeatMap as SeatMapDto } from '@/api/generated/model'
import { ExcelMenu } from '@/components/list/ExcelMenu'
import { useBreadcrumbTrail } from '@/components/shell/breadcrumb-store'
import { StatusPanel } from '@/components/StatusPanel'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { SeatMap } from '@/features/seat-map'
import { ApiError } from '@/lib/api-client'
import { errorMessage } from '@/lib/error-messages'

import {
  addElement,
  addSection,
  commit,
  createHistory,
  duplicateSection,
  type EditorState,
  type ElementType,
  fromApi,
  GRID_SNAP,
  moveElement,
  moveSection,
  redo,
  removeElement,
  removeSection,
  rotateBy,
  type Selection,
  toGuestSnapshot,
  toggleHiddenSeat,
  toRequest,
  undo,
  updateElement,
  updateSection,
} from './editorModel'
import { EditorCanvas } from './EditorCanvas'
import { LayersPanel, PropertiesPanel } from './EditorPanels'

/** Trang editor sơ đồ ghế (màn #17, ADR 0030): tải sơ đồ rồi giao cho {@link Editor}. */
export function SeatMapEditorPage({ venueId, mapId }: { venueId: string; mapId: string }) {
  const { t } = useTranslation()
  // Không tự tải lại khi quay lại tab: tải lại sẽ đặt lại editor và mất thay đổi chưa lưu. Chỉ tải lại sau khi lưu / import.
  const map = useGetAdminSeatMap(mapId, { query: { refetchOnWindowFocus: false, staleTime: Infinity } })
  const loaded = map.data?.status === 200 ? map.data.data : undefined
  useBreadcrumbTrail(loaded ? [{ label: t('adminSeatMap.title') }, { label: loaded.name }] : [])

  if (map.isPending) return <Skeleton className="h-96" aria-busy="true" />
  if (!loaded) {
    const notFound = map.error instanceof ApiError && map.error.status === 404
    return (
      <StatusPanel icon={<AlertCircle />} title={notFound ? t('adminSeatMap.notFound') : t('adminSeatMap.loadError')}>
        {!notFound && (
          <button type="button" className="text-primary text-sm underline" onClick={() => void map.refetch()}>
            {t('common.retry')}
          </button>
        )}
        <Link to="/admin/venues/$venueId/seat-maps" params={{ venueId }} className="text-primary text-sm underline">
          {t('adminSeatMap.backToList')}
        </Link>
      </StatusPanel>
    )
  }
  return <Editor key={map.dataUpdatedAt} venueId={venueId} map={loaded} />
}

interface EditorProps {
  venueId: string
  map: SeatMapDto
}

function Editor({ venueId, map }: EditorProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const baseline = useMemo(() => fromApi(map), [map])
  const [history, setHistory] = useState(() => createHistory(baseline))
  const [selection, setSelection] = useState<Selection>()
  const [hideMode, setHideMode] = useState(false)
  const [preview, setPreview] = useState(false)
  const [previewSelection, setPreviewSelection] = useState<ReadonlySet<string>>(new Set())
  const [fitSignal, setFitSignal] = useState(0)
  const update = useUpdateAdminSeatMap()
  const copy = useCopyAdminSeatMap()

  const state = history.present
  const dirty = state !== baseline
  const editable = !map.locked && !preview

  // Rời trang khi còn thay đổi chưa lưu thì trình duyệt hỏi lại.
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const apply = (next: EditorState) => setHistory((current) => commit(current, next))
  const select = (next: Selection) => {
    setSelection(next)
    setHideMode(false)
  }

  const add = (build: (current: EditorState) => { state: EditorState; key: string }, kind: 'section' | 'element') => {
    const result = build(state)
    apply(result.state)
    setSelection({ kind, key: result.key })
  }

  const remove = () => {
    if (!selection) return
    apply(selection.kind === 'section' ? removeSection(state, selection.key) : removeElement(state, selection.key))
    setSelection(undefined)
    setHideMode(false)
  }

  const duplicate = () => {
    if (selection?.kind !== 'section') return
    const result = duplicateSection(state, selection.key)
    if (result) {
      apply(result.state)
      setSelection({ kind: 'section', key: result.key })
    }
  }

  const save = () =>
    update.mutate(
      { id: map.id, data: toRequest(state) },
      {
        onSuccess: (response) => {
          if (response.status !== 200) return
          toast.success(t('adminSeatMap.saved'))
          void queryClient.invalidateQueries({ queryKey: getGetAdminSeatMapQueryKey(map.id) })
        },
        onError: (error) => toast.error(errorMessage(error, t)),
      },
    )

  const makeCopy = () =>
    copy.mutate(
      { id: map.id },
      {
        onSuccess: (response) => {
          if (response.status !== 201) return
          toast.success(t('adminSeatMap.copied'))
          void navigate({ to: '/admin/venues/$venueId/seat-maps/$mapId', params: { venueId, mapId: response.data.id } })
        },
        onError: (error) => toast.error(errorMessage(error, t)),
      },
    )

  /** Phím tắt: undo / redo, xoá, dịch bằng mũi tên, xoay R, nhân bản Ctrl+D, Esc bỏ chọn. Bỏ qua khi đang gõ vào ô nhập. */
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement
    if (target.closest('input, textarea, select, [contenteditable="true"]')) return
    const mod = event.ctrlKey || event.metaKey
    const key = event.key.toLowerCase()
    if (mod && key === 'z') {
      event.preventDefault()
      setHistory((current) => (event.shiftKey ? redo(current) : undo(current)))
      return
    }
    if (mod && key === 'y') {
      event.preventDefault()
      setHistory(redo)
      return
    }
    if (key === 'escape') {
      setSelection(undefined)
      setHideMode(false)
      return
    }
    if (!editable || !selection) return
    if (key === 'delete' || key === 'backspace') {
      event.preventDefault()
      remove()
    }
    else if (mod && key === 'd') {
      event.preventDefault()
      duplicate()
    }
    else if (key === 'r' && !mod) {
      const direction = event.shiftKey ? -1 : 1
      if (selection.kind === 'section') {
        const section = state.sections.find((item) => item.key === selection.key)
        if (section?.kind === 'GRID') apply(updateSection(state, section.key, { rotation: rotateBy(section.rotation, direction) }))
      }
      else {
        const element = state.elements.find((item) => item.key === selection.key)
        if (element) apply(updateElement(state, element.key, { rotation: rotateBy(element.rotation, direction) }))
      }
    }
    else if (key.startsWith('arrow')) {
      event.preventDefault()
      const step = event.shiftKey ? GRID_SNAP * 5 : GRID_SNAP
      const dx = key === 'arrowleft' ? -step : key === 'arrowright' ? step : 0
      const dy = key === 'arrowup' ? -step : key === 'arrowdown' ? step : 0
      if (selection.kind === 'section') {
        const section = state.sections.find((item) => item.key === selection.key)
        if (section?.kind === 'GRID') apply(moveSection(state, section.key, section.x + dx, section.y + dy))
      }
      else {
        const element = state.elements.find((item) => item.key === selection.key)
        if (element) apply(moveElement(state, element.key, element.x + dx, element.y + dy))
      }
    }
  }

  return (
    <div className="flex flex-col gap-3" onKeyDown={onKeyDown} tabIndex={-1} data-testid="seat-map-editor">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2">
          <Button asChild variant="ghost" size="icon-sm">
            <Link to="/admin/venues/$venueId/seat-maps" params={{ venueId }} aria-label={t('adminSeatMap.backToList')}>
              <ArrowLeft aria-hidden="true" />
            </Link>
          </Button>
          <h1 className="truncate text-xl font-semibold tracking-tight">{state.name}</h1>
          <Badge variant="outline">{t('adminSeatMap.version', { version: map.version })}</Badge>
          {map.locked && <Badge variant="secondary">{t('adminSeatMap.locked')}</Badge>}
          {dirty && <Badge variant="secondary">{t('adminSeatMap.unsaved')}</Badge>}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="ghost" size="icon-sm" disabled={history.past.length === 0 || preview} aria-label={t('adminSeatMap.undo')} onClick={() => setHistory(undo)}>
            <Undo2 aria-hidden="true" />
          </Button>
          <Button variant="ghost" size="icon-sm" disabled={history.future.length === 0 || preview} aria-label={t('adminSeatMap.redo')} onClick={() => setHistory(redo)}>
            <Redo2 aria-hidden="true" />
          </Button>
          <Button variant="outline" size="sm" onClick={() => setFitSignal((value) => value + 1)}>
            <Maximize aria-hidden="true" />
            {t('adminSeatMap.fit')}
          </Button>
          <Button variant={preview ? 'default' : 'outline'} size="sm" aria-pressed={preview} onClick={() => setPreview((value) => !value)}>
            <Eye aria-hidden="true" />
            {t('adminSeatMap.previewAsGuest')}
          </Button>
          {!map.locked && !dirty && <ExcelMenu type="seat-maps" seatMapId={map.id} onImported={() => void queryClient.invalidateQueries({ queryKey: getGetAdminSeatMapQueryKey(map.id) })} />}
          {map.locked ? (
            <Button size="sm" disabled={copy.isPending} onClick={makeCopy}>
              {t('adminSeatMap.makeCopy')}
            </Button>
          ) : (
            <>
              <Button variant="outline" size="sm" disabled={!dirty || update.isPending} onClick={() => { setHistory(createHistory(baseline)); setSelection(undefined) }}>
                {t('adminSeatMap.discard')}
              </Button>
              <Button size="sm" disabled={!dirty || update.isPending} onClick={save}>
                {update.isPending ? t('adminSeatMap.saving') : t('adminSeatMap.save')}
              </Button>
            </>
          )}
        </div>
      </div>

      {map.locked && (
        <p role="status" className="bg-muted rounded-md border px-3 py-2 text-sm">
          {t('adminSeatMap.lockedNotice')}
        </p>
      )}

      {preview ? (
        <SeatMap snapshot={toGuestSnapshot(state)} selectedIds={previewSelection} maxSelect={6} onSelectionChange={setPreviewSelection} onLimitReached={() => undefined} />
      ) : (
        <div className="grid items-start gap-3 lg:grid-cols-[14rem_minmax(0,1fr)_16rem]">
          <LayersPanel
            state={state}
            selection={selection}
            editable={editable}
            onSelect={select}
            onAddSection={() => add((current) => addSection(current), 'section')}
            onAddElement={(type: ElementType) => add((current) => addElement(current, type), 'element')}
          />
          <EditorCanvas
            state={state}
            selection={selection}
            editable={editable}
            hideMode={hideMode}
            fitSignal={fitSignal}
            onSelect={select}
            onMoveSection={(key, x, y) => apply(moveSection(state, key, x, y))}
            onMoveElement={(key, x, y) => apply(moveElement(state, key, x, y))}
            onToggleSeat={(key, seat) => apply(toggleHiddenSeat(state, key, seat))}
          />
          <PropertiesPanel
            state={state}
            selection={selection}
            editable={editable}
            hideMode={hideMode}
            onChange={apply}
            onToggleHideMode={() => setHideMode((value) => !value)}
            onDuplicate={duplicate}
            onRemove={remove}
          />
        </div>
      )}
    </div>
  )
}
