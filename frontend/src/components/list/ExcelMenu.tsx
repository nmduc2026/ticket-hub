import { ChevronDown, FileSpreadsheet } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { downloadServerFile } from '@/lib/csv'
import { errorMessage } from '@/lib/error-messages'

import { ImportExcelDialog, type ImportType } from './ImportExcelDialog'

const TEMPLATE_FILE_NAMES: Record<ImportType, string> = {
  'administrative-units': 'mau-don-vi-hanh-chinh.xlsx',
  venues: 'mau-dia-diem.xlsx',
  'seat-maps': 'mau-so-do-ghe.xlsx',
}

interface Props {
  type: ImportType
  seatMapId?: string
  onImported?: () => void
}

/** Menu "Excel ▾" của toolbar danh sách (docs/design/README.md §3): Import Excel, Tải file mẫu. */
export function ExcelMenu({ type, seatMapId, onImported }: Props) {
  const { t } = useTranslation()
  const [importing, setImporting] = useState(false)

  const downloadTemplate = () => {
    downloadServerFile(`/api/v1/admin/imports/templates/${type}`, TEMPLATE_FILE_NAMES[type]).catch((error: unknown) =>
      toast.error(errorMessage(error, t)),
    )
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm">
            <FileSpreadsheet aria-hidden="true" />
            {t('importExcel.menu')}
            <ChevronDown aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => setImporting(true)}>{t('importExcel.importAction')}</DropdownMenuItem>
          <DropdownMenuItem onSelect={downloadTemplate}>{t('importExcel.templateAction')}</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <ImportExcelDialog open={importing} onOpenChange={setImporting} type={type} seatMapId={seatMapId} onImported={onImported} />
    </>
  )
}
