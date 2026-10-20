/**
 * Client HTTP duy nhất của frontend; Orval gọi qua {@link apiFetch}. Trình duyệt chỉ giữ cookie SESSION và
 * XSRF-TOKEN, không có token (overview.md §13.1), nên mọi request gửi kèm cookie, header X-Requested-With để Gateway
 * trả 401 thay vì redirect, và X-XSRF-TOKEN cho request ghi.
 */

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** Lỗi theo RFC 9457 Problem Details kèm `code` nghiệp vụ (vd. SEAT_UNAVAILABLE). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly traceId: string | undefined
  readonly errors: ReadonlyArray<{ field: string; message: string }>

  constructor(status: number, problem: Record<string, unknown> = {}) {
    super(typeof problem.detail === 'string' ? problem.detail : `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = typeof problem.code === 'string' ? problem.code : undefined
    this.traceId = typeof problem.traceId === 'string' ? problem.traceId : undefined
    this.errors = Array.isArray(problem.errors) ? (problem.errors as ApiError['errors']) : []
  }
}

/** Mỗi lần người dùng bấm tạo một key mới; giữ nguyên key khi retry cùng thao tác. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const entry = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined
}

/** Gọi Gateway với cookie, header XHR và CSRF; dùng chung cho API và các endpoint của Gateway (logout). */
export function gatewayFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers)
  headers.set('X-Requested-With', 'XMLHttpRequest')
  const method = (options.method ?? 'GET').toUpperCase()
  const xsrfToken = readCookie('XSRF-TOKEN')
  if (!SAFE_METHODS.has(method) && xsrfToken) {
    headers.set('X-XSRF-TOKEN', xsrfToken)
  }
  return fetch(url, { ...options, headers, credentials: 'include' })
}

/** Mutator của Orval: trả `{ data, status, headers }`; lỗi (không phải 2xx) ném {@link ApiError}. */
export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await gatewayFetch(url, options)
  const text = [204, 205, 304].includes(response.status) ? '' : await response.text()
  const body: unknown = text ? safeJson(text) : undefined
  if (!response.ok) {
    throw new ApiError(response.status, isRecord(body) ? body : {})
  }
  return { data: body, status: response.status, headers: response.headers } as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
