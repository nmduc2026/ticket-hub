import type { ReactNode } from 'react'

import { Label } from '@/components/ui/label'

interface Props {
  id: string
  label: string
  /** Dấu `*` đỏ nằm cùng dòng với nhãn (docs/design/README.md §3). */
  required?: boolean
  /** Gợi ý ngắn đặt dưới ô nhập. */
  hint?: string
  error?: string
  children: ReactNode
}

/** Nhãn + ô nhập + gợi ý + lỗi của một trường form; lỗi gắn vào ô qua `aria-describedby` của ô nhập (id `${id}-error`). */
export function Field({ id, label, required, hint, error, children }: Props) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>
        {label}
        {required && (
          <span className="text-destructive" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </Label>
      {children}
      {hint && !error && <p className="text-muted-foreground text-xs">{hint}</p>}
      {error && (
        <p id={`${id}-error`} role="alert" className="text-destructive text-xs">
          {error}
        </p>
      )}
    </div>
  )
}
