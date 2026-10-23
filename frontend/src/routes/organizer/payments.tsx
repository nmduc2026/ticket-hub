import { createFileRoute } from '@tanstack/react-router'

import { OrganizerStripePage } from '@/features/organizer-stripe/OrganizerStripePage'

export const Route = createFileRoute('/organizer/payments')({ component: OrganizerStripePage })
