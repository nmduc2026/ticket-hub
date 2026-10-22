import type { KonvaEventObject } from 'konva/lib/Node'
import { Minus, Plus, RotateCcw } from 'lucide-react'
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Group, Layer, Line, Rect, Stage, Text } from 'react-konva'

import type { EventSeats, SeatState } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { useThemeStore } from '@/features/theme/theme-store'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/utils'

import { elementRect, fitView, type Rect as Bounds, SEAT_SIZE, seatsRect, sectionFrame, sectionRect, type View, viewForRect, zoomAt } from './seatGeometry'
import { firstSeat, NAV_KEYS, type NavKey, nextSeat } from './seatNavigation'
import { readSeatPalette, TIER_COLOR_COUNT } from './seatPalette'

export type SeatMapSnapshot = Pick<EventSeats, 'width' | 'height' | 'tiers' | 'sections' | 'elements' | 'seats'>

interface Props {
  snapshot: SeatMapSnapshot
  selectedIds: ReadonlySet<string>
  /** Số ghế tối đa mỗi lượt giữ (BR-02). */
  maxSelect: number
  onSelectionChange: (selectedIds: Set<string>) => void
  /** Gọi khi khách chọn ghế thứ `maxSelect + 1`; ghế đó không được thêm. */
  onLimitReached: () => void
  /** Khoá chọn (đang gửi yêu cầu giữ ghế, mất kết nối). */
  disabled?: boolean
}

const MIN_HEIGHT = 320
const MAX_HEIGHT = 640
const ZOOM_STEP = 1.25
const WHEEL_STEP = 1.1
/** Ghế vẽ nhỏ hơn cỡ này trên màn hình (vd. cả sơ đồ ở 390px) thì chạm vào khu sẽ phóng to khu đó trước khi chọn ghế. */
const MIN_TAPPABLE_SEAT_PX = 12

type Appearance = 'available' | 'selected' | 'sold' | 'unavailable'

/**
 * Sơ đồ ghế react-konva (ADR 0008): một Layer tĩnh cho khu và sân khấu, một Layer cho ghế, một Layer cho viền focus.
 * Ghế là component `memo` nhận giá trị đơn giản nên chỉ ghế đổi trạng thái mới vẽ lại. Zoom bằng nút, Ctrl + cuộn hoặc
 * hai ngón tay; kéo nền để di chuyển. Khu không gán hạng vé màu xám, không chọn được. Lớp bàn phím và `aria-live`
 * theo docs/design/seat-map.md §4 vì canvas không có DOM cho từng ghế.
 */
export function SeatMap({ snapshot, selectedIds, maxSelect, onSelectionChange, onLimitReached, disabled = false }: Props) {
  const { t } = useTranslation()
  const theme = useThemeStore((state) => state.resolved)
  const palette = useMemo(() => readSeatPalette(), [theme])

  const frameRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const [view, setView] = useState<View | null>(null)
  const userMovedView = useRef(false)
  const pinchDistance = useRef<number | null>(null)
  const [focusedId, setFocusedId] = useState<string | null>(null)
  const [hasFocus, setHasFocus] = useState(false)
  const [hoverId, setHoverId] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')

  const height = Math.round(Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, (width * snapshot.height) / snapshot.width)))

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
    if (width === 0 || userMovedView.current) return
    setView(fitView({ width, height }, snapshot))
  }, [width, height, snapshot.width, snapshot.height])

  const tierIndexById = useMemo(() => new Map(snapshot.tiers.map((tier, index) => [tier.id, index])), [snapshot.tiers])
  const tierBySection = useMemo(
    () => new Map(snapshot.sections.map((section) => [section.code, section.tierId])),
    [snapshot.sections],
  )
  const seatById = useMemo(() => new Map(snapshot.seats.map((seat) => [seat.id, seat])), [snapshot.seats])
  const sectionOrder = useMemo(() => snapshot.sections.map((section) => section.code), [snapshot.sections])
  const sectionBounds = useMemo(
    () =>
      snapshot.sections.flatMap((section) => {
        // Vùng bao của ghế thật: đúng với khu xoay, hàng cong và khu IMPORTED; khu chưa có ghế mới dùng khung lưới.
        const rect = seatsRect(snapshot.seats.filter((seat) => seat.sectionCode === section.code)) ?? sectionRect(section.shape)
        return rect ? [{ code: section.code, name: section.name, rect }] : []
      }),
    [snapshot.sections, snapshot.seats],
  )
  const seatsTooSmallToTap = view !== null && SEAT_SIZE * view.scale < MIN_TAPPABLE_SEAT_PX

  const appearanceOf = useCallback(
    (seat: SeatState): Appearance => {
      if (selectedIds.has(seat.id)) return 'selected'
      if (seat.status === 'HELD' || seat.status === 'SOLD') return 'sold'
      if (seat.status !== 'AVAILABLE' || !tierBySection.get(seat.sectionCode)) return 'unavailable'
      return 'available'
    },
    [selectedIds, tierBySection],
  )

  const describe = useCallback(
    (seat: SeatState): string => {
      const tier = snapshot.tiers.find((candidate) => candidate.id === tierBySection.get(seat.sectionCode))
      const appearance = appearanceOf(seat)
      return [
        t('seatMap.seat', { row: seat.row, number: seat.number }),
        tier?.name,
        tier ? formatMoney(tier.priceMinor, tier.currency) : undefined,
        t(`seatMap.state.${appearance}`),
      ]
        .filter(Boolean)
        .join(', ')
    },
    [appearanceOf, snapshot.tiers, t, tierBySection],
  )

  const activate = (seatId: string) => {
    const seat = seatById.get(seatId)
    if (!seat || disabled) return
    const appearance = appearanceOf(seat)
    if (appearance === 'selected') {
      const next = new Set(selectedIds)
      next.delete(seatId)
      onSelectionChange(next)
      setAnnouncement(t('seatMap.deselected', { seat: t('seatMap.seat', { row: seat.row, number: seat.number }) }))
    } else if (appearance !== 'available') {
      setAnnouncement(`${describe(seat)}. ${t('seatMap.notSelectable')}`)
    } else if (selectedIds.size >= maxSelect) {
      onLimitReached()
      setAnnouncement(t('seatMap.limitReached', { max: maxSelect }))
    } else {
      onSelectionChange(new Set(selectedIds).add(seatId))
      setAnnouncement(`${describe(seat)}, ${t('seatMap.state.selected')}`)
    }
  }
  // Seat memo cần callback ổn định; luôn gọi bản `activate` mới nhất (đọc lựa chọn hiện tại).
  const activateRef = useRef(activate)
  useEffect(() => {
    activateRef.current = activate
  })
  const handleActivate = useCallback((seatId: string) => activateRef.current(seatId), [])
  const handleHover = useCallback((seatId: string | null) => setHoverId(seatId), [])

  const changeView = (next: View) => {
    userMovedView.current = true
    setView(next)
  }
  const zoomFromCentre = (factor: number) => {
    if (view) changeView(zoomAt(view, factor, { x: width / 2, y: height / 2 }))
  }
  const zoomToSection = (name: string, rect: Bounds) => {
    changeView(viewForRect({ width, height }, rect))
    setAnnouncement(t('seatMap.zoomedToSection', { section: name }))
  }
  const resetView = () => {
    userMovedView.current = false
    setView(fitView({ width, height }, snapshot))
  }

  const moveFocus = (seatId: string) => {
    setFocusedId(seatId)
    const seat = seatById.get(seatId)
    if (!seat) return
    setAnnouncement(describe(seat))
    if (!view) return
    // Ghế focus nằm ngoài khung nhìn thì đưa vào giữa.
    const screenX = seat.x * view.scale + view.x
    const screenY = seat.y * view.scale + view.y
    if (screenX < 24 || screenX > width - 24 || screenY < 24 || screenY > height - 24) {
      changeView({ ...view, x: width / 2 - seat.x * view.scale, y: height / 2 - seat.y * view.scale })
    }
  }

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (NAV_KEYS.has(event.key) && snapshot.seats.length > 0) {
      event.preventDefault()
      moveFocus(focusedId ? nextSeat(snapshot.seats, focusedId, event.key as NavKey, sectionOrder) : firstSeat(snapshot.seats).id)
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      if (focusedId) activate(focusedId)
    } else if (event.key === '+' || event.key === '=') {
      zoomFromCentre(ZOOM_STEP)
    } else if (event.key === '-') {
      zoomFromCentre(1 / ZOOM_STEP)
    } else if (event.key === '0') {
      resetView()
    }
  }

  const onWheel = (event: KonvaEventObject<WheelEvent>) => {
    // Cuộn thường vẫn cuộn trang; chỉ Ctrl / ⌘ + cuộn (và cử chỉ chụm của touchpad) mới zoom.
    if (!view || !(event.evt.ctrlKey || event.evt.metaKey)) return
    event.evt.preventDefault()
    const pointer = event.target.getStage()?.getPointerPosition()
    if (pointer) changeView(zoomAt(view, event.evt.deltaY < 0 ? WHEEL_STEP : 1 / WHEEL_STEP, pointer))
  }

  const onTouchMove = (event: KonvaEventObject<TouchEvent>) => {
    const [first, second] = [event.evt.touches[0], event.evt.touches[1]]
    if (!view || !first || !second) return
    event.evt.preventDefault()
    event.target.getStage()?.stopDrag()
    const distance = Math.hypot(first.clientX - second.clientX, first.clientY - second.clientY)
    const rect = frameRef.current?.getBoundingClientRect()
    if (pinchDistance.current && rect) {
      const centre = { x: (first.clientX + second.clientX) / 2 - rect.left, y: (first.clientY + second.clientY) / 2 - rect.top }
      changeView(zoomAt(view, distance / pinchDistance.current, centre))
    }
    pinchDistance.current = distance
  }

  const hoverSeat = hoverId ? seatById.get(hoverId) : undefined
  const focusSeat = hasFocus && focusedId ? seatById.get(focusedId) : undefined

  return (
    <div className="flex flex-col gap-2">
      <div className="relative">
        <div
          ref={frameRef}
          role="application"
          aria-label={t('seatMap.label')}
          tabIndex={0}
          onKeyDown={onKeyDown}
          onFocus={() => setHasFocus(true)}
          onBlur={() => setHasFocus(false)}
          style={{ height: width === 0 ? MIN_HEIGHT : height, cursor: hoverSeat && appearanceOf(hoverSeat) === 'available' ? 'pointer' : undefined }}
          className={cn(
            'bg-card focus-visible:ring-ring/50 focus-visible:border-ring overflow-hidden rounded-xl border outline-none focus-visible:ring-3',
            disabled && 'opacity-70',
          )}
        >
          {width > 0 && view && (
            <Stage
              width={width}
              height={height}
              x={view.x}
              y={view.y}
              scaleX={view.scale}
              scaleY={view.scale}
              draggable
              onDragEnd={(event) => {
                if (event.target === event.target.getStage()) changeView({ ...view, x: event.target.x(), y: event.target.y() })
              }}
              onWheel={onWheel}
              onTouchMove={onTouchMove}
              onTouchEnd={() => (pinchDistance.current = null)}
            >
              <Layer listening={false}>
                {snapshot.elements.map((element, index) => {
                  const rect = elementRect(element.shape)
                  if (!rect || element.type === 'AISLE') return null
                  return (
                    <Group key={index} x={rect.x} y={rect.y} rotation={rect.rotation}>
                      <Rect width={rect.width} height={rect.height} cornerRadius={6} fill={palette.unavailable} />
                      {element.label && (
                        <Text
                          text={element.label.toUpperCase()}
                          width={rect.width}
                          height={rect.height}
                          align="center"
                          verticalAlign="middle"
                          fontStyle="600"
                          fontSize={14}
                          fill={palette.label}
                        />
                      )}
                    </Group>
                  )
                })}
                {snapshot.sections.map((section) => {
                  const bounds = sectionBounds.find((item) => item.code === section.code)
                  const frame = sectionFrame(section.shape) ?? (bounds ? { x: 0, y: 0, rotation: 0, rect: bounds.rect } : null)
                  if (!frame || !bounds) return null
                  const rect = bounds.rect
                  const forSale = Boolean(section.tierId)
                  return (
                    <Group key={section.code}>
                      {!forSale && (
                        <Group x={frame.x} y={frame.y} rotation={frame.rotation}>
                          <Rect {...frame.rect} cornerRadius={8} fill={palette.unavailable} opacity={0.55} />
                        </Group>
                      )}
                      <Text
                        x={rect.x}
                        y={rect.y - 16}
                        width={rect.width}
                        align="center"
                        text={section.name}
                        fontSize={12}
                        fontStyle="600"
                        fill={palette.label}
                      />
                    </Group>
                  )
                })}
              </Layer>

              <Layer>
                {snapshot.seats.map((seat) => {
                  const appearance = appearanceOf(seat)
                  const tierId = tierBySection.get(seat.sectionCode)
                  const tierColor = palette.tiers[(tierIndexById.get(tierId ?? '') ?? 0) % TIER_COLOR_COUNT]
                  return (
                    <SeatShape
                      key={seat.id}
                      id={seat.id}
                      x={seat.x}
                      y={seat.y}
                      fill={appearance === 'selected' ? palette.selected : appearance === 'available' ? tierColor : palette.unavailable}
                      stroke={appearance === 'selected' ? palette.selectedStroke : undefined}
                      marked={appearance === 'sold'}
                      markColor={palette.unavailableMark}
                      onActivate={handleActivate}
                      onHover={handleHover}
                    />
                  )
                })}
              </Layer>

              {seatsTooSmallToTap && (
                <Layer>
                  {sectionBounds.map((section) => (
                    <Rect
                      key={section.code}
                      {...section.rect}
                      fill="rgba(0,0,0,0)"
                      onClick={() => zoomToSection(section.name, section.rect)}
                      onTap={() => zoomToSection(section.name, section.rect)}
                    />
                  ))}
                </Layer>
              )}

              <Layer listening={false}>
                {focusSeat && (
                  <Rect
                    x={focusSeat.x - SEAT_SIZE / 2 - 3}
                    y={focusSeat.y - SEAT_SIZE / 2 - 3}
                    width={SEAT_SIZE + 6}
                    height={SEAT_SIZE + 6}
                    cornerRadius={6}
                    stroke={palette.focus}
                    strokeWidth={2}
                    dash={[4, 3]}
                  />
                )}
              </Layer>
            </Stage>
          )}
        </div>

        <div className="absolute top-2 right-2 flex flex-col gap-1">
          <Button type="button" size="icon-sm" variant="outline" aria-label={t('seatMap.zoomIn')} onClick={() => zoomFromCentre(ZOOM_STEP)}>
            <Plus aria-hidden="true" />
          </Button>
          <Button type="button" size="icon-sm" variant="outline" aria-label={t('seatMap.zoomOut')} onClick={() => zoomFromCentre(1 / ZOOM_STEP)}>
            <Minus aria-hidden="true" />
          </Button>
          <Button type="button" size="icon-sm" variant="outline" aria-label={t('seatMap.zoomReset')} onClick={resetView}>
            <RotateCcw aria-hidden="true" />
          </Button>
        </div>
      </div>

      <p aria-hidden="true" className="text-muted-foreground min-h-5 text-sm">
        {hoverSeat ? describe(hoverSeat) : ''}
      </p>
      <div role="status" aria-live="polite" className="sr-only">
        {announcement}
      </div>
    </div>
  )
}

interface SeatShapeProps {
  id: string
  x: number
  y: number
  fill: string
  stroke: string | undefined
  /** Ô đã bán có dấu × (không chỉ dựa vào màu). */
  marked: boolean
  markColor: string
  onActivate: (id: string) => void
  onHover: (id: string | null) => void
}

const SeatShape = memo(function SeatShape({ id, x, y, fill, stroke, marked, markColor, onActivate, onHover }: SeatShapeProps) {
  const inset = SEAT_SIZE * 0.28
  return (
    <Group
      x={x - SEAT_SIZE / 2}
      y={y - SEAT_SIZE / 2}
      onClick={() => onActivate(id)}
      onTap={() => onActivate(id)}
      onMouseEnter={() => onHover(id)}
      onMouseLeave={() => onHover(null)}
    >
      <Rect width={SEAT_SIZE} height={SEAT_SIZE} cornerRadius={4} fill={fill} stroke={stroke} strokeWidth={stroke ? 2 : 0} />
      {marked && (
        <>
          <Line points={[inset, inset, SEAT_SIZE - inset, SEAT_SIZE - inset]} stroke={markColor} strokeWidth={1.5} />
          <Line points={[SEAT_SIZE - inset, inset, inset, SEAT_SIZE - inset]} stroke={markColor} strokeWidth={1.5} />
        </>
      )}
    </Group>
  )
})
