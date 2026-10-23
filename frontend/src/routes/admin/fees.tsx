import { createFileRoute } from '@tanstack/react-router'

import { AdminFeesPage } from '@/features/admin-fees/AdminFeesPage'

export const Route = createFileRoute('/admin/fees')({ component: AdminFeesPage })
