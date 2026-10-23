import { createFileRoute } from '@tanstack/react-router'

import { AdminPaymentsPage } from '@/features/admin-payments/AdminPaymentsPage'

export const Route = createFileRoute('/admin/payments')({ component: AdminPaymentsPage })
