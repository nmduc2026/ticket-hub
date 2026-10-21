import { createFileRoute } from '@tanstack/react-router'

import { RefundFailedPage } from '@/features/admin-orders/RefundFailedPage'

export const Route = createFileRoute('/admin/orders')({ component: RefundFailedPage })
