import { createFileRoute } from '@tanstack/react-router'

import { AdminRegionsPage } from '@/features/admin-regions/AdminRegionsPage'

export const Route = createFileRoute('/admin/regions')({ component: AdminRegionsPage })
