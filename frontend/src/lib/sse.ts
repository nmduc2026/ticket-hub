/**
 * Kết nối SSE dùng chung (overview.md §12). Trình duyệt tự nối lại khi đứt; mỗi lần nối lại `onReconnect` được gọi để
 * nơi dùng tải lại trạng thái đầy đủ (snapshot) rồi nghe tiếp, vì tin phát trong lúc đứt không được gửi lại.
 * `onFailure` gọi khi trình duyệt bỏ cuộc (server trả lỗi, vd. 401/404/409): nơi dùng rơi về polling.
 */
export interface SseOptions {
  /** Xử lý theo tên sự kiện; `data` là chuỗi JSON nguyên văn. */
  handlers: Record<string, (data: string) => void>
  /** Kết nối đang mở (kể cả lần đầu): nơi dùng có thể tắt polling. */
  onOpen?: () => void
  /** Kết nối mở lại sau khi đứt: tải lại snapshot. */
  onReconnect?: () => void
  /** Kết nối đang đứt (trình duyệt đang tự thử lại hoặc đã bỏ cuộc): nơi dùng bật polling. */
  onDown?: () => void
  /** Trình duyệt bỏ cuộc hẳn. */
  onFailure?: () => void
}

/** Mở luồng SSE tới `url` (cùng origin, kèm cookie phiên); trả hàm đóng. */
export function openSse(url: string, { handlers, onOpen, onReconnect, onDown, onFailure }: SseOptions): () => void {
  if (typeof EventSource === 'undefined') {
    // Môi trường không có SSE (trình duyệt cũ, test jsdom): coi như luồng không dùng được, nơi gọi rơi về polling.
    onDown?.()
    onFailure?.()
    return () => undefined
  }
  const source = new EventSource(url, { withCredentials: true })
  let opened = false

  source.onopen = () => {
    if (opened) onReconnect?.()
    opened = true
    onOpen?.()
  }
  source.onerror = () => {
    onDown?.()
    // CLOSED: trình duyệt không thử lại nữa. Còn CONNECTING là đang tự nối lại.
    if (source.readyState === EventSource.CLOSED) onFailure?.()
  }
  for (const [name, handler] of Object.entries(handlers)) {
    source.addEventListener(name, (event) => handler((event as MessageEvent<string>).data))
  }
  return () => source.close()
}

/** Phân tích JSON của một sự kiện SSE; dữ liệu hỏng thì trả `undefined` thay vì làm vỡ giao diện. */
export function parseSse<T>(data: string): T | undefined {
  try {
    return JSON.parse(data) as T
  } catch {
    return undefined
  }
}
