import { createFileRoute } from '@tanstack/react-router'
import { z } from 'zod'

import { InfoTab } from '@/features/organizer-events/InfoTab'
import { SalesTab } from '@/features/organizer-events/SalesTab'

const editSearchSchema = z.object({ tab: z.enum(['info', 'sales']).optional() })

/** Thông tin chung và Mở bán (màn #24): hai tab của cùng một trang, tab nằm trên URL. */
export const Route = createFileRoute('/organizer/events/$eventId/edit')({
  validateSearch: (raw): z.infer<typeof editSearchSchema> => {
    const parsed = editSearchSchema.safeParse(raw)
    return parsed.success ? parsed.data : {}
  },
  component: EditRoute,
})

function EditRoute() {
  const { eventId } = Route.useParams()
  const { tab } = Route.useSearch()
  return tab === 'sales' ? <SalesTab eventId={eventId} /> : <InfoTab eventId={eventId} />
}
