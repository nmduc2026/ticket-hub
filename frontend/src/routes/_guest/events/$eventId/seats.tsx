import { createFileRoute } from '@tanstack/react-router'

// Sơ đồ chọn ghế (hạng mục 11, 12); tạm để khung để liên kết "Chọn ghế" có đích.
export const Route = createFileRoute('/_guest/events/$eventId/seats')({ component: () => null })
