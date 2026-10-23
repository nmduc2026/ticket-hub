import { Link } from '@tanstack/react-router'
import { Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'

const SLOW_AFTER_MS = 30_000

/** Chờ webhook đưa đơn về CONFIRMED sau khi Stripe nhận tiền; quá 30 giây thì nói rõ tiền đã ghi nhận (checkout.md §2). */
export function Confirming() {
  const { t } = useTranslation()
  const [slow, setSlow] = useState(false)
  useEffect(() => {
    const timer = setTimeout(() => setSlow(true), SLOW_AFTER_MS)
    return () => clearTimeout(timer)
  }, [])
  return (
    <StatusPanel
      icon={<Loader2 className="animate-spin" />}
      title={t('checkout.confirmingTitle')}
      body={slow ? t('checkout.confirmingSlow') : undefined}
    >
      {slow && (
        <Button variant="outline" asChild>
          <Link to="/me/bookings">{t('checkout.viewBookings')}</Link>
        </Button>
      )}
    </StatusPanel>
  )
}
