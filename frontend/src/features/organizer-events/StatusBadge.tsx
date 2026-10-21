import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import type { OrganizerEventSummaryStatus } from '@/api/generated/model'
import { cn } from '@/lib/utils'

const DOT: Record<OrganizerEventSummaryStatus, string> = {
  DRAFT: 'bg-muted-foreground',
  PUBLISHED: 'bg-blue-500',
  ON_SALE: 'bg-green-600',
  SUSPENDED: 'bg-amber-500',
  ENDED: 'bg-muted-foreground',
  CANCELLED: 'bg-destructive',
}

/** Badge trạng thái: viền mảnh + chấm màu + chữ, không nền pastel (docs/design/README.md §3). */
export function StatusBadge({ status }: { status: OrganizerEventSummaryStatus }) {
  const { t } = useTranslation()
  return (
    <Badge variant="outline" className="gap-1.5">
      <span className={cn('size-1.5 rounded-full', DOT[status])} aria-hidden="true" />
      {t(`organizerEvents.status.${status}`)}
    </Badge>
  )
}
