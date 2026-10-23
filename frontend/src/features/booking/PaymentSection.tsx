import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'

import type { Payment } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { formatMoney } from '@/lib/money'

import { getStripe } from './stripe'

interface Props {
  payment: Payment
  /** Khoá nút khi còn dưới 90 giây (BR-03) hoặc đang chờ xác nhận. */
  locked: boolean
  returnUrl: string
  /** Stripe đã nhận tiền ở trình duyệt; đơn chỉ CONFIRMED khi webhook về. */
  onPaid: () => void
}

/** Form Stripe Payment Element (#6). Chữ thẻ bị từ chối hiện ngay dưới form, form mở lại với cùng `clientSecret` (AC-3). */
export function PaymentSection({ payment, locked, returnUrl, onPaid }: Props) {
  const stripe = getStripe()
  const { t } = useTranslation()
  if (!stripe) return <p role="alert" className="text-destructive text-sm">{t('checkout.paymentLoadError')}</p>
  return (
    <Elements
      stripe={stripe}
      options={{ clientSecret: payment.clientSecret, appearance: { theme: 'stripe', variables: { fontFamily: 'Manrope Variable, system-ui, sans-serif' } } }}
    >
      <PayForm payment={payment} locked={locked} returnUrl={returnUrl} onPaid={onPaid} />
    </Elements>
  )
}

function PayForm({ payment, locked, returnUrl, onPaid }: Props) {
  const { t } = useTranslation()
  const stripe = useStripe()
  const elements = useElements()
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!stripe || !elements || locked || submitting) return
    setSubmitting(true)
    setMessage(null)
    const result = await stripe.confirmPayment({ elements, redirect: 'if_required', confirmParams: { return_url: returnUrl } })
    setSubmitting(false)
    if (result.error) {
      setMessage(result.error.type === 'card_error' ? t('checkout.cardDeclined') : t('checkout.authFailed'))
      return
    }
    onPaid()
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
      <PaymentElement />
      {message && (
        <div role="alert" className="border-destructive/40 bg-destructive/5 rounded-lg border px-4 py-2.5 text-sm">
          {message}
        </div>
      )}
      <Button type="submit" size="lg" disabled={!stripe || !elements || locked || submitting}>
        {submitting ? t('checkout.paying') : t('checkout.pay', { amount: formatMoney(payment.amountMinor, payment.currency) })}
      </Button>
      <p className="text-muted-foreground text-center text-xs">{t('checkout.poweredBy')}</p>
    </form>
  )
}
