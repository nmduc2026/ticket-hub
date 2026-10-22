import { useEffect, useState } from 'react'

/**
 * Số giây còn lại tới `expiresAt` (UTC từ server, BR-03); không tự đặt 10 phút ở client. `now` truyền vào được để test
 * không phải chờ. Về 0 thì dừng, không âm.
 */
export function useCountdown(expiresAt: string | undefined, now: () => number = Date.now): number | null {
  const remaining = () => (expiresAt ? Math.max(0, Math.ceil((new Date(expiresAt).getTime() - now()) / 1000)) : null)
  const [seconds, setSeconds] = useState(remaining)

  useEffect(() => {
    setSeconds(remaining())
    if (!expiresAt) return
    const timer = setInterval(() => {
      const next = remaining()
      setSeconds(next)
      if (next === 0) clearInterval(timer)
    }, 1000)
    return () => clearInterval(timer)
    // `now` ổn định trong thực tế (Date.now); test truyền hàm cố định.
  }, [expiresAt])

  return seconds
}

/** "09:41" cho đồng hồ đếm ngược. */
export function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
