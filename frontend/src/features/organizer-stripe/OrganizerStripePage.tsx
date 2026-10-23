import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  useCreateStripeLoginLink,
  useCreateStripeOnboardingLink,
  useGetStripeAccount,
} from '@/api/generated/endpoints/organizer-stripe-controller/organizer-stripe-controller'
import type { StripeAccount, StripeAccountState } from '@/api/generated/model'
import { StatusPanel } from '@/components/StatusPanel'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/error-messages'
import { formatMoney } from '@/lib/money'
import { AlertCircle, CheckCircle2, CircleDashed, Clock } from 'lucide-react'

const STEPS = ['account', 'profile', 'verification', 'payouts'] as const

/** Bước đã hoàn tất theo state hiện tại (README §5 "Luồng kết nối Stripe"). */
function stepsDone(state: StripeAccountState): number {
  switch (state) {
    case 'none':
      return 0
    case 'incomplete':
      return 1
    case 'pending':
      return 2
    case 'active':
      return 4
    case 'restricted':
      return 3
    default:
      return 0
  }
}

/** Thanh toán & Stripe (màn #26): 5 trạng thái kết nối Stripe Connect Express (overview.md §7.6, BR-05). */
export function OrganizerStripePage() {
  const { t } = useTranslation()
  const query = useGetStripeAccount({ query: { retry: false } })

  const header = (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">{t('organizerStripe.title')}</h1>
      <p className="text-muted-foreground text-sm">{t('organizerStripe.subtitle')}</p>
    </div>
  )

  if (query.isPending) {
    return (
      <section className="mx-auto flex max-w-3xl flex-col gap-6">
        {header}
        <Skeleton className="h-72 w-full" aria-busy="true" />
      </section>
    )
  }
  if (query.isError) {
    return (
      <section className="mx-auto flex max-w-3xl flex-col gap-6">
        {header}
        <StatusPanel icon={<AlertCircle />} title={t('organizerStripe.loadError')}>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        </StatusPanel>
      </section>
    )
  }

  const account = query.data.status === 200 ? query.data.data : undefined
  if (!account) return null

  return (
    <section className="mx-auto flex max-w-3xl flex-col gap-6">
      {header}
      <Steps done={stepsDone(account.state)} />
      <StateBody account={account} />
    </section>
  )
}

function Steps({ done }: { done: number }) {
  const { t } = useTranslation()
  return (
    <ol className="grid grid-cols-4 gap-2 text-center text-xs">
      {STEPS.map((step, index) => {
        const complete = index < done
        return (
          <li key={step} className="flex flex-col items-center gap-1">
            <span
              className={`flex size-6 items-center justify-center rounded-full ${complete ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'}`}
              aria-hidden="true"
            >
              {complete ? <CheckCircle2 className="size-4" /> : index + 1}
            </span>
            <span className={complete ? 'text-foreground' : 'text-muted-foreground'}>{t(`organizerStripe.steps.${step}`)}</span>
          </li>
        )
      })}
    </ol>
  )
}

function StateBody({ account }: { account: StripeAccount }) {
  switch (account.state) {
    case 'none':
      return <NoneState />
    case 'incomplete':
      return <IncompleteState account={account} />
    case 'pending':
      return <PendingState />
    case 'active':
      return <ActiveState account={account} />
    case 'restricted':
      return <RestrictedState account={account} />
    default:
      return null
  }
}

function OnboardingAction({ labelKey }: { labelKey: string }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const onboarding = useCreateStripeOnboardingLink()

  const goToStripe = () =>
    onboarding.mutate(undefined, {
      onSuccess: (response) => {
        if (response.status === 200) window.location.href = response.data.url
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    })

  return (
    <>
      <Button onClick={() => setOpen(true)}>{t(labelKey)}</Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('organizerStripe.onboardingModal.title')}</DialogTitle>
            <DialogDescription>{t('organizerStripe.onboardingModal.body')}</DialogDescription>
          </DialogHeader>
          {onboarding.isError && (
            <p role="alert" className="text-destructive text-sm">
              {errorMessage(onboarding.error, t)}
            </p>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={goToStripe} disabled={onboarding.isPending}>
              {t('organizerStripe.onboardingModal.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function NoneState() {
  const { t } = useTranslation()
  return (
    <StatusPanel icon={<CircleDashed />} title={t('organizerStripe.none.title')} body={t('organizerStripe.none.body')}>
      <OnboardingAction labelKey="organizerStripe.none.connect" />
    </StatusPanel>
  )
}

function IncompleteState({ account }: { account: StripeAccount }) {
  const { t } = useTranslation()
  return (
    <div className="bg-card flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex items-center gap-2">
        <AlertCircle className="text-muted-foreground size-5" aria-hidden="true" />
        <h2 className="font-semibold">{t('organizerStripe.incomplete.title')}</h2>
      </div>
      <RequirementsList requirements={account.requirements} />
      <div>
        <OnboardingAction labelKey="organizerStripe.incomplete.continue" />
      </div>
    </div>
  )
}

function PendingState() {
  const { t } = useTranslation()
  const query = useGetStripeAccount()
  return (
    <StatusPanel icon={<Clock />} title={t('organizerStripe.pending.title')} body={t('organizerStripe.pending.body')}>
      <Button variant="outline" size="sm" onClick={() => void query.refetch()} disabled={query.isFetching}>
        {t('organizerStripe.pending.refresh')}
      </Button>
    </StatusPanel>
  )
}

function ActiveState({ account }: { account: StripeAccount }) {
  const { t } = useTranslation()
  const login = useCreateStripeLoginLink()

  const openDashboard = () =>
    login.mutate(undefined, {
      onSuccess: (response) => {
        if (response.status === 200) window.open(response.data.url, '_blank', 'noopener')
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    })

  return (
    <div className="bg-card flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex items-center gap-2">
        <CheckCircle2 className="text-foreground size-5" aria-hidden="true" />
        <h2 className="font-semibold">{t('organizerStripe.active.title')}</h2>
      </div>
      <div className="flex flex-wrap gap-2">
        <Badge variant={account.chargesEnabled ? 'default' : 'secondary'}>{t('organizerStripe.active.chargesEnabled')}</Badge>
        <Badge variant={account.payoutsEnabled ? 'default' : 'secondary'}>{t('organizerStripe.active.payoutsEnabled')}</Badge>
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{t('organizerStripe.active.balance')}</dt>
        <dd>{account.balanceMinor === undefined ? '—' : formatMoney(account.balanceMinor, account.currency)}</dd>
      </dl>
      <div className="flex flex-wrap items-center gap-2">
        <Button onClick={openDashboard} disabled={login.isPending}>
          {t('organizerStripe.active.dashboard')}
        </Button>
        <Button variant="outline" disabled title={t('organizerStripe.active.disconnectHint')}>
          {t('organizerStripe.active.disconnect')}
        </Button>
      </div>
      {login.isError && (
        <p role="alert" className="text-destructive text-sm">
          {errorMessage(login.error, t)}
        </p>
      )}
    </div>
  )
}

function RestrictedState({ account }: { account: StripeAccount }) {
  const { t } = useTranslation()
  const login = useCreateStripeLoginLink()

  const openDashboard = () =>
    login.mutate(undefined, {
      onSuccess: (response) => {
        if (response.status === 200) window.open(response.data.url, '_blank', 'noopener')
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    })

  return (
    <div className="border-destructive/40 bg-destructive/5 flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex items-center gap-2">
        <AlertCircle className="text-destructive size-5" aria-hidden="true" />
        <h2 className="font-semibold">{t('organizerStripe.restricted.title')}</h2>
      </div>
      <p className="text-sm">{t('organizerStripe.restricted.body')}</p>
      <RequirementsList requirements={account.requirements} />
      <div>
        <Button onClick={openDashboard} disabled={login.isPending}>
          {t('organizerStripe.restricted.update')}
        </Button>
      </div>
      {login.isError && (
        <p role="alert" className="text-destructive text-sm">
          {errorMessage(login.error, t)}
        </p>
      )}
    </div>
  )
}

function RequirementsList({ requirements }: { requirements: string[] }) {
  const { t } = useTranslation()
  if (requirements.length === 0) return null
  return (
    <div className="flex flex-col gap-1">
      <p className="text-muted-foreground text-sm">{t('organizerStripe.requirementsTitle')}</p>
      <ul className="list-disc pl-5 text-sm">
        {requirements.map((requirement) => (
          <li key={requirement} className="font-mono text-xs">
            {requirement}
          </li>
        ))}
      </ul>
    </div>
  )
}
