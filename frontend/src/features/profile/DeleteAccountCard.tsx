import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useDeleteMyAccount } from '@/api/generated/endpoints/my-profile-controller/my-profile-controller'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { logout } from '@/lib/auth'
import { errorMessage } from '@/lib/error-messages'

/**
 * Xoá tài khoản (#19): gõ lại email để xác nhận; còn vé sắp diễn thì server từ chối và hộp thoại báo lý do. Xoá xong
 * thì đăng xuất (phiên ở Keycloak đã mất theo tài khoản).
 */
export function DeleteAccountCard({ email }: { email: string | undefined }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [typed, setTyped] = useState('')
  const remove = useDeleteMyAccount()
  const matches = Boolean(email) && typed.trim().toLowerCase() === email?.toLowerCase()

  const confirm = () =>
    remove.mutate({ data: { confirmEmail: typed.trim() } }, { onSuccess: () => void logout() })

  return (
    <section aria-labelledby="delete-heading" className="border-destructive/40 flex flex-col gap-2 rounded-lg border p-4">
      <h3 id="delete-heading" className="text-sm font-semibold">
        {t('profile.delete.heading')}
      </h3>
      <p className="text-muted-foreground text-sm">{t('profile.delete.body')}</p>
      <div>
        <Button variant="destructive" size="sm" onClick={() => setOpen(true)}>
          {t('profile.delete.action')}
        </Button>
      </div>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next)
          if (!next) {
            setTyped('')
            remove.reset()
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('profile.delete.confirmTitle')}</DialogTitle>
            <DialogDescription>{t('profile.delete.confirmBody')}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="delete-email">{t('profile.delete.typeEmail', { email })}</Label>
            <Input id="delete-email" type="email" autoComplete="off" value={typed} onChange={(change) => setTyped(change.target.value)} />
          </div>
          {remove.isError && (
            <p role="alert" className="text-destructive text-sm">
              {errorMessage(remove.error, t)}
            </p>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              {t('profile.delete.keep')}
            </Button>
            <Button variant="destructive" disabled={!matches || remove.isPending} onClick={confirm}>
              {t('profile.delete.action')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
