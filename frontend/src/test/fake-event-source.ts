/**
 * `EventSource` giả cho test (jsdom không có). Test điều khiển luồng bằng {@link FakeEventSource.emit},
 * {@link FakeEventSource.reconnect} và {@link FakeEventSource.fail}. Cài bằng `installFakeEventSource()`.
 */
import { vi } from 'vitest'

type Listener = (event: { data: string }) => void

export class FakeEventSource {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2
  static instances: FakeEventSource[] = []

  readonly url: string
  readyState: number = FakeEventSource.CONNECTING
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  private readonly listeners = new Map<string, Listener[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(name: string, listener: Listener) {
    this.listeners.set(name, [...(this.listeners.get(name) ?? []), listener])
  }

  close() {
    this.readyState = FakeEventSource.CLOSED
  }

  /** Kết nối mở lần đầu (hoặc nối lại sau khi đứt). */
  reconnect() {
    this.readyState = FakeEventSource.OPEN
    this.onopen?.()
  }

  /** Server gửi một sự kiện; `data` được chuyển thành JSON như trên đường truyền. */
  emit(name: string, data: unknown) {
    for (const listener of this.listeners.get(name) ?? []) listener({ data: JSON.stringify(data) })
  }

  /** Trình duyệt bỏ cuộc (server từ chối kết nối). */
  fail() {
    this.readyState = FakeEventSource.CLOSED
    this.onerror?.()
  }

  static latest(): FakeEventSource {
    const latest = FakeEventSource.instances.at(-1)
    if (!latest) throw new Error('Chưa có kết nối EventSource nào')
    return latest
  }
}

export function installFakeEventSource() {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
}
