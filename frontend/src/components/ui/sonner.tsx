import { CircleCheckIcon, InfoIcon, Loader2Icon, OctagonXIcon, TriangleAlertIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Toaster as Sonner, type ToasterProps } from 'sonner'

import { useThemeStore } from '@/features/theme/theme-store'

/**
 * Toast theo docs/design/README.md §3: góc dưới phải, nền card, viền, icon trạng thái, nút Đóng, tự ẩn sau 5 giây,
 * đọc qua aria-live. Lỗi chặn luồng hiển thị ngay trong trang, không dùng toast.
 */
const Toaster = (props: ToasterProps) => {
  const { t } = useTranslation()
  const theme = useThemeStore((state) => state.resolved)

  return (
    <Sonner
      theme={theme}
      position="bottom-right"
      duration={5000}
      closeButton
      className="toaster group"
      icons={{
        success: <CircleCheckIcon className="text-primary size-4" />,
        info: <InfoIcon className="size-4" />,
        warning: <TriangleAlertIcon className="size-4" />,
        error: <OctagonXIcon className="text-destructive size-4" />,
        loading: <Loader2Icon className="size-4 animate-spin" />,
      }}
      aria-label={t('common.notifications')}
      style={
        {
          '--normal-bg': 'var(--card)',
          '--normal-text': 'var(--card-foreground)',
          '--normal-border': 'var(--border)',
          '--border-radius': 'var(--radius)',
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { Toaster }
