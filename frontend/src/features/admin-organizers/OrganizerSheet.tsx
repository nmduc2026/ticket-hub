import { useTranslation } from 'react-i18next'

import type { AdminOrganizer } from '@/api/generated/model'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'

/** Side sheet xem hồ sơ organizer (màn #15): đọc từ dòng của danh sách, không gọi thêm API. */
export function OrganizerSheet({ organizer, onClose }: { organizer: AdminOrganizer | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const rows: Array<[string, string | undefined]> = organizer
    ? [
        [t('adminOrganizers.colType'), organizer.type ? t(`adminOrganizers.typeNames.${organizer.type}`) : undefined],
        [t('adminOrganizers.sheet.legalName'), organizer.legalName],
        [t('adminOrganizers.sheet.legalId'), organizer.legalId],
        [t('adminOrganizers.colEmail'), organizer.email],
        [t('adminOrganizers.sheet.phone'), organizer.contactPhone],
        [t('adminOrganizers.sheet.website'), organizer.website],
        [t('adminOrganizers.sheet.scale'), organizer.eventScale],
        [t('adminOrganizers.sheet.introduction'), organizer.introduction],
        [t('adminOrganizers.sheet.reason'), organizer.rejectionReason],
      ]
    : []

  return (
    <Sheet open={organizer !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{t('adminOrganizers.sheet.title', { name: organizer?.displayName })}</SheetTitle>
          <SheetDescription>{organizer ? t(`adminOrganizers.statusNames.${organizer.status}`) : ''}</SheetDescription>
        </SheetHeader>
        <dl className="flex flex-col gap-3 px-4 pb-4">
          {rows
            .filter(([, value]) => value)
            .map(([label, value]) => (
              <div key={label} className="flex flex-col gap-0.5">
                <dt className="text-muted-foreground text-xs">{label}</dt>
                <dd className="text-sm break-words whitespace-pre-line">{value}</dd>
              </div>
            ))}
        </dl>
      </SheetContent>
    </Sheet>
  )
}
