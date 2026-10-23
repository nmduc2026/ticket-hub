const formatters = new Map<string, Intl.NumberFormat>()

/**
 * Hiển thị tiền từ đơn vị nhỏ nhất của tiền tệ. VND không có phần thập phân nên 800000 là 800.000 ₫; không tự chia
 * hay nhân 100 (BR-10). Mọi nơi hiển thị tiền dùng hàm này.
 */
export function formatMoney(amountMinor: number, currency: string = 'VND'): string {
  let formatter = formatters.get(currency)
  if (!formatter) {
    formatter = new Intl.NumberFormat('vi-VN', { style: 'currency', currency })
    formatters.set(currency, formatter)
  }
  return formatter.format(currency === 'VND' ? amountMinor : amountMinor / 100)
}
