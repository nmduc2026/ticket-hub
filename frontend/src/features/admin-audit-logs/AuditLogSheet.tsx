import { useTranslation } from 'react-i18next'

import type { AdminAuditLogRow } from '@/api/generated/model'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { formatDate, formatTime } from '@/lib/date'

/** Side sheet so sánh trước / sau của một dòng audit (màn #31). */
export function AuditLogSheet({ row, onClose }: { row: AdminAuditLogRow | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  return (
    <Sheet open={row !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{row ? t(`adminAuditLogs.actions.${row.action}`, { defaultValue: row.action }) : ''}</SheetTitle>
          <SheetDescription>
            {row && `${row.actorName} · ${row.targetType} · ${formatDate(row.createdAt)} ${formatTime(row.createdAt)}`}
          </SheetDescription>
        </SheetHeader>
        {row && (
          <div className="grid grid-cols-2 gap-3 px-4 pb-4">
            <div className="flex flex-col gap-1">
              <span className="text-muted-foreground text-xs">{t('adminAuditLogs.before')}</span>
              <pre className="bg-muted overflow-x-auto rounded-md p-2 text-xs">
                {row.beforeState ? JSON.stringify(row.beforeState, null, 2) : '—'}
              </pre>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-muted-foreground text-xs">{t('adminAuditLogs.after')}</span>
              <pre className="bg-muted overflow-x-auto rounded-md p-2 text-xs">
                {row.afterState ? JSON.stringify(row.afterState, null, 2) : '—'}
              </pre>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}
