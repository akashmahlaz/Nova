import { useEffect, useMemo, useState } from 'react'
import { type AdminServiceArea, type AuditEntry, novaAdminApi } from '../../lib/api'
import { formatError } from '../../lib/errors'
import { count, date, time } from '../../lib/format'
import { PricingPanel } from './PricingPanel'
import { Button, ConfirmAction, DataTable, EmptyState, PageHeader, Panel, Segmented, Tag, notify } from './ui'

export function AreasPage({ areas, onChanged }: { areas: AdminServiceArea[]; onChanged: () => Promise<void> }) {
  const [pending, setPending] = useState<AdminServiceArea | null>(null)
  const [pricing, setPricing] = useState<string | null>(null)
  const active = areas.filter((a) => a.active).length
  return (
    <div className="space-y-6">
      <PageHeader title="Service areas" meta={`${count(active)} of ${count(areas.length)} taking bookings`} />
      <DataTable
        id="areas"
        label="Service areas"
        rows={areas}
        rowKey={(a) => a.id}
        empty={{ title: 'No service areas yet' }}
        summary={`${count(active)} of ${count(areas.length)} taking bookings`}
        columns={[
          { id: 'area', header: 'Area', hideable: false, className: 'font-medium text-fg', cell: (a) => a.name },
          { id: 'region', header: 'Region', className: 'font-mono text-fg-2', cell: (a) => a.region_code },
          { id: 'zone', header: 'Currency and time zone', className: 'text-fg-2', cell: (a) => `${a.currency} · ${a.timezone}` },
          { id: 'rates', header: 'Fare rates', align: 'right', className: 'font-mono text-fg-2 tabular-nums', cell: (a) => count(a.rate_count) },
          { id: 'trips', header: 'Trips', align: 'right', className: 'font-mono text-fg-2 tabular-nums', cell: (a) => count(a.ride_count) },
          { id: 'bookings', header: 'Bookings', cell: (a) => (a.active ? <Tag tone="live">Open</Tag> : <Tag>Paused</Tag>) },
          {
            id: 'actions',
            header: 'Actions',
            hideable: false,
            align: 'right',
            cell: (a) => (
              <div className="flex justify-end gap-2">
                <Button size="sm" variant="secondary" onClick={() => setPricing(a.id)}>
                  Prices and rules
                </Button>
                <Button size="sm" variant={a.active ? 'danger' : 'secondary'} onClick={() => setPending(a)}>
                  {a.active ? 'Pause bookings' : 'Open for bookings'}
                </Button>
              </div>
            ),
          },
        ]}
      />
      <PricingPanel areaId={pricing} onClose={() => setPricing(null)} />
      {pending && (
        <ConfirmAction
          open
          onOpenChange={(o) => !o && setPending(null)}
          tone={pending.active ? 'danger' : 'primary'}
          title={pending.active ? `Pause bookings in ${pending.name}?` : `Open ${pending.name} for bookings?`}
          description={
            pending.active
              ? `Riders in ${pending.name} can't book new trips until you open it again. Trips already under way carry on.`
              : `Riders in ${pending.name} can book trips straight away at the area's fare rates.`
          }
          confirmLabel={pending.active ? 'Pause bookings' : 'Open for bookings'}
          formatError={formatError}
          onConfirm={async () => {
            await novaAdminApi.toggleServiceArea(pending.id, !pending.active)
            notify.done(pending.active ? `${pending.name} paused` : `${pending.name} is open`)
            await onChanged()
          }}
        />
      )}
    </div>
  )
}

type Kind = 'all' | AuditEntry['target_type']

export function ActivityPage({ version }: { version: number }) {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [kind, setKind] = useState<Kind>('all')
  useEffect(() => {
    novaAdminApi
      .listAudit({ limit: 300 })
      .then((e) => {
        setEntries(e)
        setError(null)
      })
      .catch((e) => setError(formatError(e)))
  }, [version])
  const groups = useMemo(() => {
    const list = (entries ?? []).filter((e) => kind === 'all' || e.target_type === kind)
    const out: { day: string; items: AuditEntry[] }[] = []
    for (const e of list) {
      const day = date(e.created_at)
      if (out.at(-1)?.day !== day) out.push({ day, items: [] })
      out.at(-1)!.items.push(e)
    }
    return out
  }, [entries, kind])

  return (
    <div className="space-y-6">
      <PageHeader title="Activity" meta="Every admin action, who took it and why. Entries can't be edited or removed." />
      <Panel
        title={
          <Segmented
            label="Activity filter"
            value={kind}
            onValueChange={setKind}
            options={[
              { value: 'all', label: 'All' },
              { value: 'driver', label: 'Drivers' },
              { value: 'user', label: 'Accounts' },
              { value: 'media', label: 'Files' },
              { value: 'service_area', label: 'Areas' },
            ]}
          />
        }
        bodyClassName="px-5 py-2"
      >
        {error && (
          <p role="alert" className="py-4 text-sm text-bad">
            {error}
          </p>
        )}
        {entries && groups.length === 0 && <EmptyState title="No activity yet" body="Approvals, role changes and other admin actions appear here." />}
        {groups.map((g) => (
          <section key={g.day} aria-label={g.day} className="py-3">
            <h3 className="pb-2 text-xs text-fg-3">{g.day}</h3>
            <ol>
              {g.items.map((e) => (
                <li key={e.id} className="grid grid-cols-[64px_1fr] gap-4 border-t border-line py-3 first:border-t-0">
                  <span className="font-mono text-xs leading-5 text-fg-3">{time(e.created_at)}</span>
                  <div className="min-w-0">
                    <p className="text-sm text-fg">{e.summary}</p>
                    <p className="text-xs text-fg-3">
                      by {e.admin_name}
                      {e.reason && <span className="text-fg-2"> · “{e.reason}”</span>}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </section>
        ))}
      </Panel>
    </div>
  )
}
