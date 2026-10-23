/** Ảnh tải lên (banner, ảnh đại diện): kiểm sớm ở client cho người dùng biết ngay; backend kiểm lại theo nội dung file. */
const MAX_BYTES = 2 * 1024 * 1024

export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/** Mã lỗi (khớp mã của backend) nếu file không nhận được, ngược lại null. */
export function imageFileError(file: File): 'UNSUPPORTED_IMAGE_TYPE' | 'IMAGE_TOO_LARGE' | null {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) return 'UNSUPPORTED_IMAGE_TYPE'
  if (file.size > MAX_BYTES) return 'IMAGE_TOO_LARGE'
  return null
}
