import { createFileRoute } from '@tanstack/react-router'

import { HomePage } from '@/features/events/HomePage'

export const Route = createFileRoute('/_guest/')({ component: HomePage })
