import { AlertCircle, Inbox, SearchX } from 'lucide-react'
import { type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

export interface Column<Row> {
  id: string
  header: string
  cell: (row: Row) => ReactNode
  /** Tiền và giờ không xuống dòng: truyền `whitespace-nowrap`. */
  className?: string
}

export interface PaginationState {
  /** Trang tính từ 0, khớp tham số `page` của API. */
  page: number
  size: number
  total: number
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

interface Props<Row> {
  columns: Column<Row>[]
  hiddenColumns?: ReadonlySet<string>
  /** `undefined` khi chưa có dữ liệu (đang tải hoặc lỗi). */
  rows: Row[] | undefined
  getRowId: (row: Row) => string
  getRowLabel: (row: Row) => string
  isLoading: boolean
  isError: boolean
  /** Dòng phụ dưới "Không tải được dữ liệu", vd. mã tra cứu khi lỗi 5xx. */
  errorDetail?: string
  onRetry: () => void
  hasFilters: boolean
  onResetFilters: () => void
  emptyTitle: string
  emptyBody?: string
  selection?: { selected: ReadonlySet<string>; onChange: (selected: Set<string>) => void }
  pagination?: PaginationState
  actionsHeader?: string
  rowActions?: (row: Row) => ReactNode
}

const PAGE_SIZES = [10, 20, 50]
const SKELETON_ROWS = 5

/**
 * Bảng dùng chung: header `secondary`, đủ 4 trạng thái (đang tải, rỗng, không có kết quả khớp bộ lọc, lỗi + thử lại),
 * chọn dòng, phân trang. Dữ liệu và bộ lọc do component cha giữ (thường là URL + TanStack Query).
 */
export function DataTable<Row>({
  columns,
  hiddenColumns,
  rows,
  getRowId,
  getRowLabel,
  isLoading,
  isError,
  errorDetail,
  onRetry,
  hasFilters,
  onResetFilters,
  emptyTitle,
  emptyBody,
  selection,
  pagination,
  actionsHeader,
  rowActions,
}: Props<Row>) {
  const { t } = useTranslation()
  const visible = columns.filter((column) => !hiddenColumns?.has(column.id))
  const columnCount = visible.length + (selection ? 1 : 0) + (rowActions ? 1 : 0)
  const selected = selection?.selected
  const rowIds = rows?.map(getRowId) ?? []
  const selectedOnPage = rowIds.filter((id) => selected?.has(id)).length
  const allState = selectedOnPage === 0 ? false : selectedOnPage === rowIds.length ? true : 'indeterminate'

  const toggleAll = (checked: boolean) => {
    if (!selection) return
    const next = new Set(selection.selected)
    rowIds.forEach((id) => (checked ? next.add(id) : next.delete(id)))
    selection.onChange(next)
  }
  const toggleRow = (id: string, checked: boolean) => {
    if (!selection) return
    const next = new Set(selection.selected)
    if (checked) next.add(id)
    else next.delete(id)
    selection.onChange(next)
  }

  const showRows = !isLoading && !isError && rows && rows.length > 0
  const showEmpty = !isLoading && !isError && rows && rows.length === 0

  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              {selection && (
                <TableHead className="w-10">
                  <Checkbox
                    checked={allState}
                    disabled={!showRows}
                    onCheckedChange={(checked) => toggleAll(checked === true)}
                    aria-label={t('list.selectAllRows')}
                  />
                </TableHead>
              )}
              {visible.map((column) => (
                <TableHead key={column.id}>{column.header}</TableHead>
              ))}
              {rowActions && <TableHead className="w-12 text-right">{actionsHeader}</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading &&
              Array.from({ length: SKELETON_ROWS }, (_, index) => (
                <TableRow key={index} aria-hidden="true">
                  <TableCell colSpan={columnCount}>
                    <Skeleton className="h-5 w-full" />
                  </TableCell>
                </TableRow>
              ))}

            {isError && (
              <StateRow colSpan={columnCount} icon={<AlertCircle />} title={t('list.loadError')} body={errorDetail}>
                <Button variant="outline" size="sm" onClick={onRetry}>
                  {t('common.retry')}
                </Button>
              </StateRow>
            )}

            {showEmpty && hasFilters && (
              <StateRow colSpan={columnCount} icon={<SearchX />} title={t('list.emptyFiltered')}>
                <Button variant="outline" size="sm" onClick={onResetFilters}>
                  {t('list.resetFilters')}
                </Button>
              </StateRow>
            )}
            {showEmpty && !hasFilters && (
              <StateRow colSpan={columnCount} icon={<Inbox />} title={emptyTitle} body={emptyBody} />
            )}

            {showRows &&
              rows.map((row) => {
                const id = getRowId(row)
                const isSelected = Boolean(selected?.has(id))
                return (
                  <TableRow key={id} data-state={isSelected ? 'selected' : undefined}>
                    {selection && (
                      <TableCell className="w-10">
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={(checked) => toggleRow(id, checked === true)}
                          aria-label={t('list.selectRow', { name: getRowLabel(row) })}
                        />
                      </TableCell>
                    )}
                    {visible.map((column) => (
                      <TableCell key={column.id} className={column.className}>
                        {column.cell(row)}
                      </TableCell>
                    ))}
                    {rowActions && <TableCell className="text-right">{rowActions(row)}</TableCell>}
                  </TableRow>
                )
              })}
          </TableBody>
        </Table>
      </div>

      {pagination && showRows && <TableFooter pagination={pagination} selectedCount={selected?.size} />}
    </div>
  )
}

function StateRow({
  colSpan,
  icon,
  title,
  body,
  children,
}: {
  colSpan: number
  icon: ReactNode
  title: string
  body?: string
  children?: ReactNode
}) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableCell colSpan={colSpan}>
        <div role="status" className="flex flex-col items-center gap-2 py-10 text-center">
          <span className="text-muted-foreground [&_svg]:size-8" aria-hidden="true">
            {icon}
          </span>
          <p className="font-semibold">{title}</p>
          {body && <p className="text-muted-foreground text-sm">{body}</p>}
          {children}
        </div>
      </TableCell>
    </TableRow>
  )
}

function TableFooter({ pagination, selectedCount }: { pagination: PaginationState; selectedCount: number | undefined }) {
  const { t } = useTranslation()
  const { page, size, total, onPageChange, onSizeChange } = pagination
  const pageCount = Math.max(1, Math.ceil(total / size))

  return (
    <div className="text-muted-foreground flex flex-wrap items-center justify-between gap-3 text-sm">
      <p>{t('list.rowsSelected', { selected: selectedCount ?? 0, total })}</p>
      <div className="flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2">
          <span id="page-size-label">{t('list.pageSize')}</span>
          <Select value={String(size)} onValueChange={(value) => onSizeChange(Number(value))}>
            <SelectTrigger size="sm" className="w-20" aria-labelledby="page-size-label">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZES.map((option) => (
                <SelectItem key={option} value={String(option)}>
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <span className={cn('text-foreground')}>{t('list.page', { page: page + 1, count: pageCount })}</span>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
            {t('list.prev')}
          </Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= pageCount} onClick={() => onPageChange(page + 1)}>
            {t('list.next')}
          </Button>
        </div>
      </div>
    </div>
  )
}
