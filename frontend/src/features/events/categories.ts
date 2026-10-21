/**
 * Danh mục sự kiện cố định (docs/design/README.md, màn #1–2). Backend lưu `category` là chuỗi tự do và lọc khớp
 * đúng chuỗi, nên organizer cũng chọn từ danh sách này khi tạo sự kiện.
 */
export const EVENT_CATEGORIES = [
  'Nhạc sống',
  'Sân khấu & Nghệ thuật',
  'Hài kịch',
  'Thể thao',
  'Hội thảo & Workshop',
  'Lễ hội',
] as const

export type EventCategory = (typeof EVENT_CATEGORIES)[number]
