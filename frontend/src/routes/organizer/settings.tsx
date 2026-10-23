import { createFileRoute } from '@tanstack/react-router'

import { OrganizerSettingsPage } from '@/features/organizer-settings/OrganizerSettingsPage'

export const Route = createFileRoute('/organizer/settings')({ component: OrganizerSettingsPage })
