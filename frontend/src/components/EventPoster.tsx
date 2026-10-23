import { cn } from '@/lib/utils'

const POSTER_COLORS = ['#0f766e', '#1d4ed8', '#a21caf', '#c2410c', '#4d7c0f', '#475569']

function colorFor(title: string): string {
  let hash = 0
  for (const character of title) {
    hash = (hash * 31 + character.charCodeAt(0)) >>> 0
  }
  return POSTER_COLORS[hash % POSTER_COLORS.length]
}

interface EventPosterProps {
  title: string
  bannerUrl?: string | null
  className?: string
}

/** Ảnh banner của sự kiện; chưa có ảnh thì dùng poster màu tự động (nền màu + tên sự kiện). */
export function EventPoster({ title, bannerUrl, className }: EventPosterProps) {
  if (bannerUrl) {
    return <img src={bannerUrl} alt="" className={cn('size-full object-cover', className)} loading="lazy" />
  }
  return (
    <div
      className={cn('flex size-full items-end p-4 text-lg font-semibold text-white', className)}
      style={{ backgroundColor: colorFor(title) }}
      aria-hidden="true"
    >
      <span className="line-clamp-3">{title}</span>
    </div>
  )
}
