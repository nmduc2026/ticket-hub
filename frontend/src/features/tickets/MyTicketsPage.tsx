import { AlertCircle, Ticket as TicketIcon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { StatusPanel } from '@/components/StatusPanel'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'

import { ticketTab, type TicketTab } from './ticketGroups'
import { TicketRow } from './TicketRow'
import { useMyTickets } from './useMyTickets'

const PAGE_SIZE = 10
const TABS: TicketTab[] = ['upcoming', 'past', 'refunded']

/** Vé của tôi (#8): tab Sắp diễn ra / Đã qua / Đã hoàn, tìm theo sự kiện hoặc ghế, phân trang. */
export function MyTicketsPage() {
  const { t } = useTranslation()
  const { tickets, isPending, isError, refetch } = useMyTickets()
  const [tab, setTab] = useState<TicketTab>('upcoming')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)

  const byTab = useMemo(() => {
    const groups: Record<TicketTab, typeof tickets> = { upcoming: [], past: [], refunded: [] }
    for (const ticket of tickets) groups[ticketTab(ticket)].push(ticket)
    return groups
  }, [tickets])

  if (isPending) return <Skeleton className="mx-auto h-80 max-w-2xl" aria-busy="true" />
  if (isError) {
    return (
      <StatusPanel icon={<AlertCircle />} title={t('tickets.loadError')}>
        <Button variant="outline" size="sm" onClick={() => void refetch()}>
          {t('common.retry')}
        </Button>
      </StatusPanel>
    )
  }

  const needle = query.trim().toLowerCase()
  const filtered = byTab[tab].filter(
    (ticket) =>
      !needle || `${ticket.eventTitle} ${ticket.tierName} ${ticket.seat.row}${ticket.seat.number}`.toLowerCase().includes(needle),
  )
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const current = Math.min(page, pageCount)
  const rows = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4">
      <h1 className="text-2xl font-semibold tracking-tight">{t('tickets.title')}</h1>
      <Input
        type="search"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value)
          setPage(1)
        }}
        placeholder={t('tickets.search')}
        aria-label={t('tickets.search')}
      />
      <Tabs
        value={tab}
        onValueChange={(value) => {
          setTab(value as TicketTab)
          setPage(1)
        }}
      >
        <TabsList>
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(`tickets.tab.${key}`)} ({byTab[key].length})
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {rows.length === 0 ? (
        <StatusPanel icon={<TicketIcon />} title={t('tickets.empty')} />
      ) : (
        <ul className="flex flex-col gap-2">
          {rows.map((ticket) => (
            <TicketRow key={ticket.ticketId} ticket={ticket} />
          ))}
        </ul>
      )}

      {pageCount > 1 && (
        <nav className="flex items-center justify-between text-sm" aria-label={t('tickets.paging')}>
          <Button variant="outline" size="sm" disabled={current === 1} onClick={() => setPage(current - 1)}>
            {t('tickets.prev')}
          </Button>
          <span>{t('tickets.page', { page: current, count: pageCount })}</span>
          <Button variant="outline" size="sm" disabled={current === pageCount} onClick={() => setPage(current + 1)}>
            {t('tickets.next')}
          </Button>
        </nav>
      )}
    </div>
  )
}
