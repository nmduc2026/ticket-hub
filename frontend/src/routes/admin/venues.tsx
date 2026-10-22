import { createFileRoute } from '@tanstack/react-router'

import { AdminVenuesPage } from '@/features/admin-venues/AdminVenuesPage'

export const Route = createFileRoute('/admin/venues')({ component: AdminVenuesPage })
