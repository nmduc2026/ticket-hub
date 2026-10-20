import { createFileRoute } from '@tanstack/react-router'

import { GuestLayout } from '@/components/shell/GuestLayout'

/** Nhóm route dùng layout khách hàng (header + footer): trang công khai và các trang "của tôi". */
export const Route = createFileRoute('/_guest')({ component: GuestLayout })
