/** Ô CSV: bọc nháy kép khi có dấu phẩy, nháy kép hoặc xuống dòng (RFC 4180). */
function cell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value
}

/** Tải xuống file CSV (BOM để Excel đọc đúng tiếng Việt). */
export function downloadCsv(filename: string, header: string[], rows: string[][]): void {
  const text = [header, ...rows].map((line) => line.map(cell).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
