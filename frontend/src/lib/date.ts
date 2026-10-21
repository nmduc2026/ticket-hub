/** Mọi thời gian từ API là UTC (BR-11); hiển thị theo giờ Việt Nam. */
export const DISPLAY_TIME_ZONE = 'Asia/Ho_Chi_Minh'

/** Việt Nam không có giờ mùa hè nên độ lệch cố định +07:00. */
const VN_OFFSET_MS = 7 * 60 * 60 * 1000

function formatter(options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('vi-VN', { timeZone: DISPLAY_TIME_ZONE, ...options })
}

const WEEKDAY_SHORT = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']

/** Phần ngày giờ theo giờ Việt Nam. */
function vnParts(iso: string): { year: number; month: number; day: number; hour: number; minute: number; weekday: number } {
  const local = new Date(new Date(iso).getTime() + VN_OFFSET_MS)
  return {
    year: local.getUTCFullYear(),
    month: local.getUTCMonth() + 1,
    day: local.getUTCDate(),
    hour: local.getUTCHours(),
    minute: local.getUTCMinutes(),
    weekday: local.getUTCDay(),
  }
}

const pad = (value: number) => String(value).padStart(2, '0')

/** Chip ngày trên thẻ sự kiện: "T7, 15/11". */
export function formatDateChip(iso: string): string {
  const { weekday, day, month } = vnParts(iso)
  return `${WEEKDAY_SHORT[weekday]}, ${pad(day)}/${pad(month)}`
}

/** Giờ: "20:00". */
export function formatTime(iso: string): string {
  const { hour, minute } = vnParts(iso)
  return `${pad(hour)}:${pad(minute)}`
}

/** Ngày đầy đủ: "15/11/2026". */
export function formatDate(iso: string): string {
  const { day, month, year } = vnParts(iso)
  return `${pad(day)}/${pad(month)}/${year}`
}

/** "Thứ Bảy, 15/11/2026 · 20:00–22:30" cho trang chi tiết. */
export function formatEventRange(startsAt: string, endsAt: string): string {
  const weekday = formatter({ weekday: 'long' }).format(new Date(startsAt))
  const capitalised = weekday.charAt(0).toUpperCase() + weekday.slice(1)
  return `${capitalised}, ${formatDate(startsAt)} · ${formatTime(startsAt)}–${formatTime(endsAt)}`
}

/** ISO UTC của 00:00 theo giờ Việt Nam của ngày chứa `reference`, cộng `dayOffset` ngày. */
export function startOfVnDay(reference: Date, dayOffset = 0): string {
  const local = new Date(reference.getTime() + VN_OFFSET_MS)
  const startUtc = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate() + dayOffset) - VN_OFFSET_MS
  return new Date(startUtc).toISOString()
}

/** ISO UTC của 00:00 ngày đầu tháng (theo giờ Việt Nam) cộng `monthOffset` tháng. */
export function startOfVnMonth(reference: Date, monthOffset = 0): string {
  const local = new Date(reference.getTime() + VN_OFFSET_MS)
  const startUtc = Date.UTC(local.getUTCFullYear(), local.getUTCMonth() + monthOffset, 1) - VN_OFFSET_MS
  return new Date(startUtc).toISOString()
}

/** Thứ trong tuần (0 = Chủ nhật) của thời điểm `reference` theo giờ Việt Nam. */
export function vnWeekday(reference: Date): number {
  return new Date(reference.getTime() + VN_OFFSET_MS).getUTCDay()
}

/** "2026-11-15" + "20:00" (giờ Việt Nam) → ISO UTC "2026-11-15T13:00:00.000Z"; `undefined` khi thiếu hoặc sai định dạng. */
export function vnInputsToIso(date: string, time: string): string | undefined {
  const [year, month, day] = date.split('-').map(Number)
  const [hour, minute] = time.split(':').map(Number)
  if ([year, month, day, hour, minute].some((part) => !Number.isInteger(part))) return undefined
  return new Date(Date.UTC(year, month - 1, day, hour, minute) - VN_OFFSET_MS).toISOString()
}

/** ISO UTC → giá trị cho ô nhập ngày / giờ theo giờ Việt Nam (`''` khi chưa có). */
export function isoToVnInputs(iso: string | undefined): { date: string; time: string } {
  if (!iso) return { date: '', time: '' }
  const { year, month, day, hour, minute } = vnParts(iso)
  return { date: `${year}-${pad(month)}-${pad(day)}`, time: `${pad(hour)}:${pad(minute)}` }
}
