import { useState } from 'react'

/** Một giá trị chuỗi nhớ trên máy (tên cổng, bật / tắt tiếng). Storage lỗi hoặc bị chặn thì vẫn chạy, chỉ không nhớ. */
export function useStoredSetting(key: string, fallback: string): [string, (value: string) => void] {
  const [value, setValue] = useState(() => {
    try {
      return localStorage.getItem(key) ?? fallback
    } catch {
      return fallback
    }
  })
  const update = (next: string) => {
    setValue(next)
    try {
      localStorage.setItem(key, next)
    } catch {
      // không nhớ được: bỏ qua
    }
  }
  return [value, update]
}
