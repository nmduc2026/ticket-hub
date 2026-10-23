import { useEffect } from 'react'
import { create } from 'zustand'

/** Mục breadcrumb sau mục của sidebar, vd. tên sự kiện và tab đang mở (màn #13, #24). Mục cuối là trang hiện tại. */
export interface Crumb {
  label: string
}

interface BreadcrumbState {
  trail: Crumb[]
  setTrail: (trail: Crumb[]) => void
}

export const useBreadcrumbStore = create<BreadcrumbState>((set) => ({
  trail: [],
  setTrail: (trail) => set({ trail }),
}))

/** Trang gắn thêm mục vào breadcrumb của khung khi đang hiển thị; rời trang thì gỡ. */
export function useBreadcrumbTrail(trail: Crumb[]): void {
  const setTrail = useBreadcrumbStore((state) => state.setTrail)
  const key = trail.map((crumb) => crumb.label).join('\u0000')
  useEffect(() => {
    setTrail(trail)
    return () => setTrail([])
    // `key` đại diện cho nội dung của `trail`: tránh đặt lại mỗi lần render vì mảng mới.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, setTrail])
}
