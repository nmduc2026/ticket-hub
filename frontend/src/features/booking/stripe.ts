import { loadStripe, type Stripe } from '@stripe/stripe-js'

import { STRIPE_PUBLISHABLE_KEY } from '@/lib/config'

let cached: Promise<Stripe | null> | null = null

/** Tải Stripe.js một lần; `null` khi chưa cấu hình khoá công khai. */
export function getStripe(): Promise<Stripe | null> | null {
  if (!STRIPE_PUBLISHABLE_KEY) return null
  cached ??= loadStripe(STRIPE_PUBLISHABLE_KEY)
  return cached
}
