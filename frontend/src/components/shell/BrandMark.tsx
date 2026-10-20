import { Ticket } from 'lucide-react'

/** Ô logo vuông của Ticket Hub (sidebar, header). */
export function BrandMark() {
  return (
    <span
      className="bg-primary text-primary-foreground grid size-8 shrink-0 place-items-center rounded-lg"
      aria-hidden="true"
    >
      <Ticket className="size-4" />
    </span>
  )
}
