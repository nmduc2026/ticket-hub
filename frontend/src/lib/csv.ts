import { ApiError, gatewayFetch } from '@/lib/api-client'

/**
 * Gọi endpoint danh sách với `Accept: text/csv` (cùng bộ lọc, overview.md §11.1) và tải xuống nguyên văn (server đã
 * có BOM). Dùng cho CSV toàn bộ theo bộ lọc, khác {@link downloadCsv} (chỉ xuất các dòng đang hiện ở client).
 */
export async function downloadServerCsv(url: string, filename: string): Promise<void> {
  const response = await gatewayFetch(url, { headers: { Accept: 'text/csv' } })
  if (!response.ok) {
    let problem: Record<string, unknown> = {}
    try {
      problem = (await response.json()) as Record<string, unknown>
    }
    catch {
      // body không phải JSON (vd. lỗi hạ tầng): bỏ qua, ApiError dùng problem rỗng.
    }
    throw new ApiError(response.status, problem)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  link.click()
  URL.revokeObjectURL(objectUrl)
}

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
