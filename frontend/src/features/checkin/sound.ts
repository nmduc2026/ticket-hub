/**
 * Âm báo kết quả check-in tạo bằng Web Audio, không cần tải file. AudioContext chỉ chạy sau thao tác của người dùng
 * (trình duyệt di động chặn tự phát), nên `unlockSound` được gọi khi bấm "Bắt đầu quét".
 */
let context: AudioContext | null = null

export function unlockSound(): void {
  try {
    context ??= new AudioContext()
    void context.resume()
  } catch {
    context = null // trình duyệt không hỗ trợ: bỏ qua âm thanh, kết quả vẫn hiện bằng chữ và màu
  }
}

function beep(frequency: number, startAt: number, duration: number): void {
  if (!context) return
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  oscillator.frequency.value = frequency
  gain.gain.setValueAtTime(0.0001, startAt)
  gain.gain.exponentialRampToValueAtTime(0.3, startAt + 0.01)
  gain.gain.exponentialRampToValueAtTime(0.0001, startAt + duration)
  oscillator.connect(gain).connect(context.destination)
  oscillator.start(startAt)
  oscillator.stop(startAt + duration + 0.02)
}

/** Hợp lệ: một tiếng cao ngắn. */
export function playAccepted(): void {
  if (context) beep(1175, context.currentTime, 0.18)
  navigator.vibrate?.(60)
}

/** Từ chối: hai tiếng thấp. */
export function playRejected(): void {
  if (context) {
    beep(300, context.currentTime, 0.2)
    beep(300, context.currentTime + 0.26, 0.2)
  }
  navigator.vibrate?.([200, 80, 200])
}
