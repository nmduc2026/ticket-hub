import i18n from 'i18next'

import { ApiError } from './api-client'
import { errorMessage, isRetryable } from './error-messages'

const t = i18n.t.bind(i18n)

describe('errorMessage', () => {
  it('map mã lỗi nghiệp vụ sang tiếng Việt, không lộ mã kỹ thuật', () => {
    const message = errorMessage(new ApiError(409, { code: 'SEAT_UNAVAILABLE' }), t)
    expect(message).toBe('Ghế vừa có người giữ. Chọn ghế khác.')
    expect(message).not.toContain('SEAT_UNAVAILABLE')
  })

  it('điền tham số vào thông báo', () => {
    expect(errorMessage(new ApiError(422, { code: 'TICKET_LIMIT_EXCEEDED' }), t, { max: 4 })).toContain('tối đa 4 vé')
  })

  it('mã lạ và 5xx dùng thông báo chung; lỗi mạng có thông báo riêng', () => {
    expect(errorMessage(new ApiError(422, { code: 'SOMETHING_NEW' }), t)).toBe('Có lỗi xảy ra. Thử lại sau.')
    expect(errorMessage(new ApiError(503), t)).toBe('Máy chủ đang bận. Thử lại sau ít phút.')
    expect(errorMessage(new TypeError('Failed to fetch'), t)).toBe('Không kết nối được. Kiểm tra mạng rồi thử lại.')
  })
})

describe('isRetryable', () => {
  it('chỉ lỗi mạng và 5xx mới thử lại với cùng key; 4xx là kết quả rõ ràng', () => {
    expect(isRetryable(new TypeError('Failed to fetch'))).toBe(true)
    expect(isRetryable(new ApiError(503))).toBe(true)
    expect(isRetryable(new ApiError(409, { code: 'SEAT_UNAVAILABLE' }))).toBe(false)
  })
})
