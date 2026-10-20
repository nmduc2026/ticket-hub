/** Chữ cái đầu của họ tên để làm avatar (vd. "Quản Trị" -> "QT"); rơi về tên đăng nhập khi thiếu họ tên. */
export function initials(fullName: string | null | undefined, username: string | null | undefined): string {
  const words = (fullName?.trim() || username?.trim() || '?').split(/\s+/)
  const letters = words.length === 1 ? words[0].slice(0, 2) : words[0][0] + words[words.length - 1][0]
  return letters.toUpperCase()
}
