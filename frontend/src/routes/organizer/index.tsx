import { createFileRoute } from '@tanstack/react-router'

import { OrganizerDashboardPage } from '@/features/organizer-dashboard/OrganizerDashboardPage'

export const Route = createFileRoute('/organizer/')({ component: OrganizerDashboardPage })
