import { createFileRoute } from '@tanstack/react-router'

import { AdminDashboardPage } from '@/features/admin-dashboard/AdminDashboardPage'

export const Route = createFileRoute('/admin/')({ component: AdminDashboardPage })
