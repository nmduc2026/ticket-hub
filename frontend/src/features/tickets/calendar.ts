/**
 * File lịch `.ics` (RFC 5545) cho "Thêm vào lịch" ở vé (#9), sinh ở trình duyệt. Giờ ghi theo `Asia/Ho_Chi_Minh` kèm
 * VTIMEZONE (+07:00, không có giờ mùa hè) để lịch hiện đúng giờ diễn ở mọi nơi.
 */
const TIME_ZONE = 'Asia/Ho_Chi_Minh'
const VN_OFFSET_MS = 7 * 3_600_000

export interface CalendarEvent {
  uid: string
  title: string
  location: string
  startsAt: string
  endsAt?: string
  description?: string
}

/** "20261115T200000": giờ Việt Nam của một thời điểm UTC. */
function vnLocal(iso: string): string {
  const shifted = new Date(new Date(iso).getTime() + VN_OFFSET_MS).toISOString()
  return shifted.slice(0, 19).replace(/[-:]/g, '')
}

function utcStamp(date: Date): string {
  return date.toISOString().slice(0, 19).replace(/[-:]/g, '') + 'Z'
}

/** Thoát ký tự đặc biệt của giá trị TEXT (RFC 5545 §3.3.11). */
function escapeText(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n')
}

export function buildIcs(event: CalendarEvent, now: Date = new Date()): string {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Ticket Hub//Ve su kien//VI',
    'CALSCALE:GREGORIAN',
    'BEGIN:VTIMEZONE',
    `TZID:${TIME_ZONE}`,
    'BEGIN:STANDARD',
    'DTSTART:19700101T000000',
    'TZOFFSETFROM:+0700',
    'TZOFFSETTO:+0700',
    'TZNAME:ICT',
    'END:STANDARD',
    'END:VTIMEZONE',
    'BEGIN:VEVENT',
    `UID:${event.uid}@tickethub`,
    `DTSTAMP:${utcStamp(now)}`,
    `DTSTART;TZID=${TIME_ZONE}:${vnLocal(event.startsAt)}`,
    ...(event.endsAt ? [`DTEND;TZID=${TIME_ZONE}:${vnLocal(event.endsAt)}`] : []),
    `SUMMARY:${escapeText(event.title)}`,
    `LOCATION:${escapeText(event.location)}`,
    ...(event.description ? [`DESCRIPTION:${escapeText(event.description)}`] : []),
    'END:VEVENT',
    'END:VCALENDAR',
  ]
  return lines.join('\r\n') + '\r\n'
}

/** Tải file `.ics` về máy (trình duyệt mở bằng ứng dụng lịch). */
export function downloadIcs(filename: string, content: string): void {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/calendar;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
