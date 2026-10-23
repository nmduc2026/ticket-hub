import type { ReactNode } from 'react'

/** Trạng thái chặn cả màn hình (hết giờ, không tìm thấy, lỗi tải): icon, tiêu đề, mô tả ngắn và nút hành động. */
export function StatusPanel({
  icon,
  title,
  body,
  children,
}: {
  icon: ReactNode
  title: string
  body?: string
  children?: ReactNode
}) {
  return (
    <div role="alert" className="mx-auto flex max-w-lg flex-col items-center gap-3 py-16 text-center">
      <span className="text-muted-foreground [&_svg]:size-10" aria-hidden="true">
        {icon}
      </span>
      <h1 className="text-xl font-semibold">{title}</h1>
      {body && <p className="text-muted-foreground text-sm">{body}</p>}
      {children && <div className="mt-2 flex flex-wrap justify-center gap-2">{children}</div>}
    </div>
  )
}
