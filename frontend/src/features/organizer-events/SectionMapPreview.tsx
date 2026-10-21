import { useTranslation } from 'react-i18next'

import type { SeatMap } from '@/api/generated/model'
import { elementRect, sectionRect, TIER_COLOR_COUNT } from '@/features/seat-map'

interface Props {
  seatMap: SeatMap
  /** `sectionId → tierId` của các khu đã gán hạng vé; khu không có trong đây là không bán. */
  tierBySection: ReadonlyMap<string, string>
  /** Thứ tự hạng vé quyết định màu (cùng bảng màu với sơ đồ chọn ghế của khách). */
  tierIds: string[]
}

/**
 * Xem trước kết quả gán hạng vé cho khu trên sơ đồ (màn #13): khu tô màu theo hạng vé, khu chưa gán màu xám.
 * SVG thay vì canvas vì chỉ có vài chục khu, và đọc được bằng trình đọc màn hình.
 */
export function SectionMapPreview({ seatMap, tierBySection, tierIds }: Props) {
  const { t } = useTranslation()
  return (
    <svg
      viewBox={`0 0 ${seatMap.width} ${seatMap.height}`}
      role="img"
      aria-label={t('organizerEvents.tiers.previewLabel')}
      className="bg-card h-auto w-full rounded-lg border"
    >
      {seatMap.elements.map((element) => {
        const rect = elementRect(element.shape)
        if (!rect || element.type === 'AISLE') return null
        return (
          <g key={element.id} transform={`rotate(${rect.rotation} ${rect.x} ${rect.y})`}>
            <rect x={rect.x} y={rect.y} width={rect.width} height={rect.height} rx={6} fill="var(--seat-off)" />
            {element.label && (
              <text
                x={rect.x + rect.width / 2}
                y={rect.y + rect.height / 2}
                textAnchor="middle"
                dominantBaseline="middle"
                fontSize={16}
                fontWeight={600}
                fill="var(--muted-foreground)"
              >
                {element.label.toUpperCase()}
              </text>
            )}
          </g>
        )
      })}
      {seatMap.sections.map((section) => {
        const rect = sectionRect(section.shape)
        if (!rect) return null
        const tierId = tierBySection.get(section.id)
        const index = tierId ? tierIds.indexOf(tierId) : -1
        const color = index >= 0 ? `var(--tier-${(index % TIER_COLOR_COUNT) + 1})` : 'var(--seat-off)'
        return (
          <g key={section.id} data-testid={`section-${section.code}`} data-assigned={index >= 0}>
            <rect {...rect} rx={8} fill={color} fillOpacity={index >= 0 ? 0.85 : 0.6} />
            <text
              x={rect.x + rect.width / 2}
              y={rect.y + rect.height / 2}
              textAnchor="middle"
              dominantBaseline="middle"
              fontSize={20}
              fontWeight={600}
              fill={index >= 0 ? 'var(--background)' : 'var(--muted-foreground)'}
            >
              {section.name}
            </text>
          </g>
        )
      })}
    </svg>
  )
}
