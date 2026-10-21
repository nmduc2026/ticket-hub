import { create } from 'zustand'

interface SeatSelectionState {
  /** Sự kiện của lựa chọn hiện tại; đổi sự kiện thì lựa chọn cũ bị bỏ. */
  eventId: string | null
  selectedIds: ReadonlySet<string>
  /** Dùng cho trang chọn ghế: mở sự kiện mới thì xoá lựa chọn của sự kiện trước. */
  open: (eventId: string) => void
  set: (selectedIds: ReadonlySet<string>) => void
  /** Giữ lại đúng những ghế còn chọn được (sau khi tải lại trạng thái ghế). */
  retain: (stillAvailable: ReadonlySet<string>) => void
  clear: () => void
}

/** Ghế đang chọn: trạng thái giao diện thuần client, chưa gửi server (chỉ `POST /bookings` mới giữ ghế). */
export const useSeatSelection = create<SeatSelectionState>((set, get) => ({
  eventId: null,
  selectedIds: new Set(),
  open: (eventId) => {
    if (get().eventId !== eventId) set({ eventId, selectedIds: new Set() })
  },
  set: (selectedIds) => set({ selectedIds }),
  retain: (stillAvailable) =>
    set((state) => ({ selectedIds: new Set([...state.selectedIds].filter((id) => stillAvailable.has(id))) })),
  clear: () => set({ selectedIds: new Set() }),
}))
