import { Bell } from 'lucide-react'
import { useRef } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

/**
 * Chuông thông báo ở khung của cả 3 vai trò. Chưa có dữ liệu thật (thông báo trong app làm ở Sprint 4), nên popover
 * chỉ có trạng thái trống; popover đóng khi bấm ra ngoài hoặc nhấn Esc, và luôn trả focus về nút chuông.
 */
export function NotificationBell() {
  const { t } = useTranslation()
  const trigger = useRef<HTMLButtonElement>(null)

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button ref={trigger} variant="ghost" size="icon" aria-label={t('notifications.title')}>
          <Bell />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        className="w-80 p-0"
        onCloseAutoFocus={(event) => {
          // Radix không trả focus khi đóng bằng click ra ngoài; thiết kế yêu cầu luôn trả về nút mở.
          event.preventDefault()
          trigger.current?.focus()
        }}
      >
        <div className="border-b px-4 py-3 text-sm font-semibold">{t('notifications.title')}</div>
        <p className="text-muted-foreground px-4 py-8 text-center text-sm">{t('notifications.empty')}</p>
      </PopoverContent>
    </Popover>
  )
}
