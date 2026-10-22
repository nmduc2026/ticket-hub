import type { KonvaEventObject } from 'konva/lib/Node'
import { memo, useEffect, useMemo, useRef, useState } from 'react'
import { Group, Layer, Rect, Shape, Stage, Text } from 'react-konva'

import { generateSeats } from '@/features/seat-map/generateSeats'
import { fitView, SEAT_SIZE, sectionFrame, type View, zoomAt } from '@/features/seat-map/seatGeometry'
import { readSeatPalette } from '@/features/seat-map/seatPalette'
import { useThemeStore } from '@/features/theme/theme-store'

import { type EditorElement, type EditorSection, type EditorState, GRID_SNAP, type GridSection, type Selection, snap } from './editorModel'

const MIN_HEIGHT = 420
const MAX_HEIGHT = 720
const WHEEL_STEP = 1.1

interface Props {
  state: EditorState
  selection: Selection
  editable: boolean
  /** Bật thì bấm vào ghế của khu để ẩn / hiện ghế thay vì chọn khu. */
  hideMode: boolean
  /** Tăng lên để đặt lại khung nhìn vừa sơ đồ ("Vừa khung"). */
  fitSignal: number
  onSelect: (selection: Selection) => void
  onMoveSection: (key: string, x: number, y: number) => void
  onMoveElement: (key: string, x: number, y: number) => void
  onToggleSeat: (key: string, seat: string) => void
}

/**
 * Canvas editor sơ đồ ghế (ADR 0030, ADR 0008). Mỗi khu `GRID` là một nhóm đặt tại ghế đầu và xoay quanh ghế đó, đúng như
 * `generateSeats`; ghế của khu được vẽ bằng MỘT `Shape` (một đường dẫn gồm mọi ô ghế) nên sơ đồ 20.000 ghế vẫn chỉ tốn một
 * lệnh vẽ mỗi khu, và vùng bấm chỉ là khung khu thay vì từng ghế. Kéo thả bắt vào lưới khi thả.
 */
export function EditorCanvas({ state, selection, editable, hideMode, fitSignal, onSelect, onMoveSection, onMoveElement, onToggleSeat }: Props) {
  const theme = useThemeStore((current) => current.resolved)
  const palette = useMemo(() => readSeatPalette(), [theme])
  const surface = useMemo(() => getComputedStyle(document.documentElement).getPropertyValue('--card').trim() || '#fff', [theme])
  const border = useMemo(() => getComputedStyle(document.documentElement).getPropertyValue('--border').trim() || '#ddd', [theme])

  const frameRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const [view, setView] = useState<View | null>(null)
  const height = Math.round(Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, (width * state.height) / state.width)))

  useEffect(() => {
    const frame = frameRef.current
    if (!frame) return
    const measure = () => setWidth(frame.clientWidth)
    measure()
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(measure)
    observer.observe(frame)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (width > 0) setView(fitView({ width, height }, state))
  }, [width, height, state.width, state.height, fitSignal])

  const onWheel = (event: KonvaEventObject<WheelEvent>) => {
    if (!event.evt.ctrlKey || !view) return
    event.evt.preventDefault()
    const pointer = event.target.getStage()?.getPointerPosition()
    if (!pointer) return
    setView(zoomAt(view, event.evt.deltaY < 0 ? WHEEL_STEP : 1 / WHEEL_STEP, pointer))
  }

  const clearSelection = (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
    if (event.target === event.target.getStage() || event.target.name() === 'map-background') onSelect(undefined)
  }

  return (
    <div ref={frameRef} className="bg-muted/40 overflow-hidden rounded-lg border" data-testid="editor-canvas">
      {view && width > 0 && (
        <Stage
          width={width}
          height={height}
          x={view.x}
          y={view.y}
          scaleX={view.scale}
          scaleY={view.scale}
          draggable
          onDragEnd={(event) => {
            if (event.target === event.target.getStage()) setView({ ...view, x: event.target.x(), y: event.target.y() })
          }}
          onWheel={onWheel}
          onMouseDown={clearSelection}
          onTouchStart={clearSelection}
          style={{ cursor: hideMode ? 'crosshair' : undefined }}
        >
          <Layer>
            <Rect name="map-background" width={state.width} height={state.height} fill={surface} stroke={border} strokeWidth={1 / view.scale} />
            <GridLines width={state.width} height={state.height} color={border} />
            {state.elements.map((element) => (
              <ElementNode
                key={element.key}
                element={element}
                selected={selection?.kind === 'element' && selection.key === element.key}
                editable={editable && !hideMode}
                fill={palette.unavailable}
                text={palette.label}
                focus={palette.focus}
                onSelect={onSelect}
                onMove={onMoveElement}
              />
            ))}
            {state.sections.map((section) => (
              <SectionNode
                key={section.key}
                section={section}
                selected={selection?.kind === 'section' && selection.key === section.key}
                editable={editable && !hideMode}
                hideMode={hideMode && editable}
                seatColor={palette.tiers[0]}
                text={palette.label}
                focus={palette.focus}
                scale={view.scale}
                onSelect={onSelect}
                onMove={onMoveSection}
                onToggleSeat={onToggleSeat}
              />
            ))}
          </Layer>
        </Stage>
      )}
    </div>
  )
}

/** Lưới chấm mờ mỗi 50 đơn vị (bội của bước bắt lưới), vẽ một lần bằng một Shape không nhận chuột. */
const GridLines = memo(function GridLines({ width, height, color }: { width: number; height: number; color: string }) {
  return (
    <Shape
      listening={false}
      sceneFunc={(context, shape) => {
        context.beginPath()
        for (let x = 50; x < width; x += 50) {
          context.moveTo(x, 0)
          context.lineTo(x, height)
        }
        for (let y = 50; y < height; y += 50) {
          context.moveTo(0, y)
          context.lineTo(width, y)
        }
        context.strokeShape(shape)
      }}
      stroke={color}
      strokeWidth={0.5}
      opacity={0.6}
    />
  )
})

interface ElementProps {
  element: EditorElement
  selected: boolean
  editable: boolean
  fill: string
  text: string
  focus: string
  onSelect: (selection: Selection) => void
  onMove: (key: string, x: number, y: number) => void
}

const ElementNode = memo(function ElementNode({ element, selected, editable, fill, text, focus, onSelect, onMove }: ElementProps) {
  return (
    <Group
      x={element.x}
      y={element.y}
      rotation={element.rotation}
      draggable={editable}
      onClick={() => onSelect({ kind: 'element', key: element.key })}
      onTap={() => onSelect({ kind: 'element', key: element.key })}
      onDragStart={() => onSelect({ kind: 'element', key: element.key })}
      onDragEnd={(event) => {
        const x = snap(event.target.x())
        const y = snap(event.target.y())
        event.target.position({ x, y })
        onMove(element.key, x, y)
      }}
    >
      <Rect width={element.w} height={element.h} cornerRadius={6} fill={fill} opacity={element.type === 'AISLE' ? 0.35 : 0.8} stroke={selected ? focus : undefined} strokeWidth={selected ? 3 : 0} />
      {element.label !== '' && <Text width={element.w} height={element.h} align="center" verticalAlign="middle" text={element.label} fontSize={14} fontStyle="600" fill={text} listening={false} />}
    </Group>
  )
})

interface SectionProps {
  section: EditorSection
  selected: boolean
  editable: boolean
  hideMode: boolean
  seatColor: string
  text: string
  focus: string
  scale: number
  onSelect: (selection: Selection) => void
  onMove: (key: string, x: number, y: number) => void
  onToggleSeat: (key: string, seat: string) => void
}

const SectionNode = memo(function SectionNode(props: SectionProps) {
  if (props.section.kind === 'IMPORTED') return null // API chưa trả toạ độ ghế của khu nhập từ Excel nên chưa vẽ được.
  return <GridSectionNode {...props} section={props.section} />
})

function GridSectionNode({ section, selected, editable, hideMode, seatColor, text, focus, onSelect, onMove, onToggleSeat }: SectionProps & { section: GridSection }) {
  // Toạ độ cục bộ của MỌI ghế (kể cả ghế ẩn): đổi vị trí / góc xoay / ghế ẩn không phải sinh lại.
  const { rows, seatsPerRow, rowGap, seatGap, curve, numbering } = section
  const all = useMemo(
    () => generateSeats({ x: 0, y: 0, rotation: 0, rows, seatsPerRow, rowGap, seatGap, curve, numbering, hiddenSeats: [] }),
    [rows, seatsPerRow, rowGap, seatGap, curve, numbering],
  )
  const hidden = useMemo(() => new Set(section.hiddenSeats), [section.hiddenSeats])
  const frame = sectionFrame({ ...section, kind: 'GRID' })
  const half = SEAT_SIZE / 2

  const toggleNearest = (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
    const point = event.currentTarget.getRelativePointerPosition()
    if (!point) return
    let best: { label: string; distance: number } | undefined
    for (const seat of all) {
      const distance = Math.hypot(seat.x - point.x, seat.y - point.y)
      if (distance <= SEAT_SIZE && (!best || distance < best.distance)) best = { label: `${seat.rowLabel}${seat.seatNumber}`, distance }
    }
    if (best) onToggleSeat(section.key, best.label)
  }

  return (
    <Group
      x={section.x}
      y={section.y}
      rotation={section.rotation}
      draggable={editable}
      onClick={hideMode ? toggleNearest : () => onSelect({ kind: 'section', key: section.key })}
      onTap={hideMode ? toggleNearest : () => onSelect({ kind: 'section', key: section.key })}
      onDragStart={() => onSelect({ kind: 'section', key: section.key })}
      onDragEnd={(event) => {
        const x = snap(event.target.x(), GRID_SNAP)
        const y = snap(event.target.y(), GRID_SNAP)
        event.target.position({ x, y })
        onMove(section.key, x, y)
      }}
    >
      {frame && <Rect {...frame.rect} cornerRadius={8} fill={selected ? focus : seatColor} opacity={selected ? 0.14 : 0.06} stroke={selected ? focus : undefined} strokeWidth={selected ? 2 : 0} />}
      <Shape
        listening={false}
        fill={seatColor}
        sceneFunc={(context, shape) => {
          context.beginPath()
          for (const seat of all) {
            if (!hidden.has(`${seat.rowLabel}${seat.seatNumber}`)) context.rect(seat.x - half, seat.y - half, SEAT_SIZE, SEAT_SIZE)
          }
          context.fillShape(shape)
        }}
      />
      {(hideMode || hidden.size > 0) && (
        <Shape
          listening={false}
          stroke={text}
          strokeWidth={1}
          dash={[3, 3]}
          sceneFunc={(context, shape) => {
            context.beginPath()
            for (const seat of all) {
              if (hidden.has(`${seat.rowLabel}${seat.seatNumber}`)) context.rect(seat.x - half, seat.y - half, SEAT_SIZE, SEAT_SIZE)
            }
            context.strokeShape(shape)
          }}
        />
      )}
      {frame && <Text x={frame.rect.x} y={frame.rect.y - 16} width={frame.rect.width} align="center" text={section.name} fontSize={12} fontStyle="600" fill={text} listening={false} />}
    </Group>
  )
}
