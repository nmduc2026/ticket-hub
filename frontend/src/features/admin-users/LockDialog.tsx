import { useTranslation } from 'react-i18next'

import type { AdminUser } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface Props {
  /** Người sắp bị khoá / mở khoá; `undefined` thì đóng. Đang khoá → hỏi mở khoá, và ngược lại. */
  user: AdminUser | undefined
  pending: boolean
  onCancel: () => void
  onConfirm: (user: AdminUser) => void
}

/** Hộp xác nhận khoá / mở khoá tài khoản (màn #28): khoá đăng xuất người dùng ngay nên luôn hỏi lại. */
export function LockDialog({ user, pending, onCancel, onConfirm }: Props) {
  const { t } = useTranslation()
  const key = user?.enabled === false ? 'unlockDialog' : 'lockDialog'
  const name = user?.fullName ?? user?.email ?? ''

  return (
    <Dialog open={user !== undefined} onOpenChange={(open) => !open && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(`adminUsers.${key}.title`, { name })}</DialogTitle>
          <DialogDescription>{t(`adminUsers.${key}.description`)}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button
            type="button"
            variant={user?.enabled === false ? 'default' : 'destructive'}
            disabled={pending}
            onClick={() => user && onConfirm(user)}
          >
            {t(`adminUsers.${key}.confirm`)}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
