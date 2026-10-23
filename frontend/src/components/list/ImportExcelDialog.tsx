import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useImportExcel } from '@/api/generated/endpoints/admin-import-controller/admin-import-controller'
import type { ImportExcelOnDuplicate, ImportResult } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { downloadServerFile } from '@/lib/csv'
import { errorMessage } from '@/lib/error-messages'

export type ImportType = 'administrative-units' | 'venues' | 'seat-maps'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  type: ImportType
  /** Sơ đồ đích, chỉ với `seat-maps`. */
  seatMapId?: string
  /** Gọi sau khi import thật xong, để màn danh sách tải lại. */
  onImported?: () => void
}

/**
 * Modal import Excel 3 bước (docs/design/README.md §3): chọn file → kiểm tra (dry-run: tổng / hợp lệ / lỗi theo
 * dòng–cột, tải file lỗi) → kết quả (thêm mới / cập nhật / bỏ qua). Dòng lỗi không chặn dòng hợp lệ.
 */
export function ImportExcelDialog({ open, onOpenChange, type, seatMapId, onImported }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | undefined>()
  const [onDuplicate, setOnDuplicate] = useState<ImportExcelOnDuplicate>('UPDATE')
  const [checked, setChecked] = useState<ImportResult | undefined>()
  const [done, setDone] = useState<ImportResult | undefined>()
  const importExcel = useImportExcel()

  const reset = () => {
    setFile(undefined)
    setChecked(undefined)
    setDone(undefined)
    setOnDuplicate('UPDATE')
    importExcel.reset()
  }

  const close = (next: boolean) => {
    if (!next) reset()
    onOpenChange(next)
  }

  const run = (dryRun: boolean) => {
    if (!file) return
    importExcel.mutate(
      { type, data: { file }, params: { dryRun, onDuplicate, seatMapId } },
      {
        onSuccess: (response) => {
          if (response.status !== 200) return
          if (dryRun) {
            setChecked(response.data)
            return
          }
          setDone(response.data)
          void queryClient.invalidateQueries()
          onImported?.()
        },
      },
    )
  }

  const downloadErrors = (id: string) => {
    downloadServerFile(`/api/v1/admin/imports/${id}/errors`, 'loi-import.xlsx').catch((error: unknown) =>
      toast.error(errorMessage(error, t)),
    )
  }

  const result = done ?? checked
  const step = done ? 3 : checked ? 2 : 1

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('importExcel.title')}</DialogTitle>
          <DialogDescription>{step === 3 ? t('importExcel.resultTitle') : t('importExcel.columnsHeading')}</DialogDescription>
        </DialogHeader>

        {step === 1 && (
          <div className="flex flex-col gap-4">
            <p className="text-muted-foreground font-mono text-xs">{t(`importExcel.columns.${type}`)}</p>
            <div className="flex items-center gap-3">
              <input
                ref={fileInput}
                type="file"
                accept=".xlsx"
                className="sr-only"
                aria-label={t('importExcel.chooseFile')}
                onChange={(event) => setFile(event.target.files?.[0])}
              />
              <Button type="button" variant="outline" size="sm" onClick={() => fileInput.current?.click()}>
                {t('importExcel.chooseFile')}
              </Button>
              <span className="text-muted-foreground truncate text-sm">{file?.name ?? t('importExcel.noFile')}</span>
            </div>
            <div className="flex flex-col gap-2">
              <Label>{t('importExcel.onDuplicate')}</Label>
              <RadioGroup value={onDuplicate} onValueChange={(value) => setOnDuplicate(value as ImportExcelOnDuplicate)} className="flex gap-6">
                <div className="flex items-center gap-2">
                  <RadioGroupItem value="UPDATE" id="import-update" />
                  <Label htmlFor="import-update">{t('importExcel.update')}</Label>
                </div>
                <div className="flex items-center gap-2">
                  <RadioGroupItem value="SKIP" id="import-skip" />
                  <Label htmlFor="import-skip">{t('importExcel.skip')}</Label>
                </div>
              </RadioGroup>
            </div>
          </div>
        )}

        {step === 2 && result && (
          <div className="flex flex-col gap-3">
            <Summary
              items={[
                [t('importExcel.total'), result.totalRows],
                [t('importExcel.valid'), result.validRows],
                [t('importExcel.errors'), result.errorRows],
              ]}
            />
            {result.errors.length > 0 && (
              <>
                <p className="text-muted-foreground text-sm">{t('importExcel.errorsHint')}</p>
                <div className="max-h-64 overflow-auto rounded-md border">
                  <table className="w-full text-sm">
                    <thead className="bg-secondary sticky top-0 text-left font-semibold">
                      <tr>
                        <th className="px-3 py-1.5">{t('importExcel.errorRow')}</th>
                        <th className="px-3 py-1.5">{t('importExcel.errorColumn')}</th>
                        <th className="px-3 py-1.5">{t('importExcel.errorMessage')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {result.errors.map((error) => (
                        <tr key={`${error.row}-${error.column}`} className="border-t">
                          <td className="px-3 py-1.5 whitespace-nowrap">{error.row}</td>
                          <td className="px-3 py-1.5 font-mono whitespace-nowrap">{error.column}</td>
                          <td className="px-3 py-1.5">{error.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {result.hasErrorFile && (
                  <Button type="button" variant="outline" size="sm" className="self-start" onClick={() => downloadErrors(result.id)}>
                    {t('importExcel.downloadErrors')}
                  </Button>
                )}
              </>
            )}
          </div>
        )}

        {step === 3 && result && (
          <div className="flex flex-col gap-3">
            <Summary
              items={[
                [t('importExcel.resultCreated'), result.created],
                [t('importExcel.resultUpdated'), result.updated],
                [t('importExcel.resultSkipped'), result.skipped],
                [t('importExcel.errors'), result.errorRows],
              ]}
            />
            {result.hasErrorFile && (
              <Button type="button" variant="outline" size="sm" className="self-start" onClick={() => downloadErrors(result.id)}>
                {t('importExcel.downloadErrors')}
              </Button>
            )}
          </div>
        )}

        {importExcel.isError && (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(importExcel.error, t)}
          </p>
        )}

        <DialogFooter>
          {step === 1 && (
            <Button type="button" disabled={!file || importExcel.isPending} onClick={() => run(true)}>
              {importExcel.isPending ? t('importExcel.checking') : t('importExcel.check')}
            </Button>
          )}
          {step === 2 && result && (
            <>
              <Button type="button" variant="outline" disabled={importExcel.isPending} onClick={reset}>
                {t('importExcel.back')}
              </Button>
              <Button type="button" disabled={result.validRows === 0 || importExcel.isPending} onClick={() => run(false)}>
                {importExcel.isPending ? t('importExcel.importing') : t('importExcel.importNow')}
              </Button>
            </>
          )}
          {step === 3 && (
            <Button type="button" onClick={() => close(false)}>
              {t('importExcel.done')}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Summary({ items }: { items: Array<[string, number]> }) {
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {items.map(([label, value]) => (
        <div key={label} className="rounded-md border px-3 py-2">
          <dt className="text-muted-foreground text-xs">{label}</dt>
          <dd className="text-lg font-semibold tabular-nums">{value}</dd>
        </div>
      ))}
    </dl>
  )
}
