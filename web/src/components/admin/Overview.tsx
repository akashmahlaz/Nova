import type { AdminDriver, AdminMediaItem, AdminOverview, AdminRide, AuditEntry } from '../../lib/api'
import { ago, change, count, fullName, money } from '../../lib/format'
import { readyForReview, splitVehicle } from './DriversPage'
import { TripsChart } from './TripsChart'
import { Avatar, Button, EmptyState, Panel, Plate, StatusTag, Tag, isLiveTrip } from './ui'
import { initials } from '../../lib/format'

export function Overview({
  overview,
  drivers,
  rides,
  media,
  activity,
  onOpenDriver,
  onOpenTrip,
  go,
}: {
  overview: AdminOverview | null
  drivers: AdminDriver[]
  rides: AdminRide[]
  media: AdminMediaItem[]
  activity: AuditEntry[]
  onOpenDriver: (id: string) => void
  onOpenTrip: (id: string) => void
  go: (page: 'drivers' | 'trips' | 'files' | 'activity') => void
}) {
  const days = overview?.daily_rides ?? []
  const thisWeek = days.slice(-7)
  const lastWeek = days.length > 7 ? days.slice(0, days.length - 7) : []
  const sum = (pts: typeof days, k: 'completed' | 'fare_cents') => pts.reduce((a, p) => a + p[k], 0)
  const currency = rides[0]?.currency ?? 'NZD'
  const tripsDelta = change(sum(thisWeek, 'completed'), sum(lastWeek, 'completed'), 'the 7 days before')
  const faresDelta = change(sum(thisWeek, 'fare_cents'), sum(lastWeek, 'fare_cents'), 'the 7 days before')

  const ready = drivers.filter(readyForReview)
  const applying = drivers.filter((d) => d.status === 'pending' && !readyForReview(d)).length
  const docsToReview = media.filter((m) => m.status === 'uploaded').length
  const live = rides.filter((r) => isLiveTrip(r.status))
  const recent = (live.length ? [...live, ...rides.filter((r) => !isLiveTrip(r.status))] : rides).slice(0, 6)

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat
          label="Drivers online now"
          value={count(overview?.online_drivers ?? 0)}
          foot={<Tag tone={(overview?.online_drivers ?? 0) > 0 ? 'live' : 'neutral'}>{count(overview?.approved_drivers ?? 0)} approved in total</Tag>}
        />
        <Stat label="Trips under way" value={count(overview?.active_rides ?? 0)} foot={<span className="text-fg-2">{count(overview?.scheduled_rides ?? 0)} scheduled for later</span>} />
        <Stat label="Completed trips, last 7 days" value={count(sum(thisWeek, 'completed'))} foot={<Delta {...tripsDelta} />} />
        <Stat label="Fares, last 7 days" value={money(sum(thisWeek, 'fare_cents'), currency)} foot={<Delta {...faresDelta} />} />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <TripsChart data={days} currency={currency} />
        </div>
        <Panel
          title="Needs your attention"
          meta={ready.length + docsToReview === 0 ? 'All caught up' : undefined}
          bodyClassName="p-2"
        >
          {ready.length === 0 && docsToReview === 0 && applying === 0 ? (
            <EmptyState title="Nothing waiting" body="New driver applications and documents to check show up here." />
          ) : (
            <ul>
              {ready.slice(0, 5).map((d) => {
                const v = splitVehicle(d.vehicle)
                return (
                  <li key={d.user_id}>
                    <button type="button" onClick={() => onOpenDriver(d.user_id)} className="flex w-full cursor-pointer items-center gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-sunken">
                      <Avatar text={initials(d)} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-fg">{fullName(d)}</p>
                        <p className="text-xs text-fg-3">Application ready · {d.submitted_at ? ago(d.submitted_at) : ''}</p>
                      </div>
                      {v.plate && <Plate plate={v.plate} />}
                    </button>
                  </li>
                )
              })}
              {docsToReview > 0 && (
                <li>
                  <button type="button" onClick={() => go('files')} className="flex w-full cursor-pointer items-center justify-between gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-sunken">
                    <span className="text-sm text-fg">Documents to check</span>
                    <span className="font-mono text-sm text-fg-2">{count(docsToReview)}</span>
                  </button>
                </li>
              )}
              {applying > 0 && (
                <li>
                  <button type="button" onClick={() => go('drivers')} className="flex w-full cursor-pointer items-center justify-between gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-sunken">
                    <span className="text-sm text-fg-2">Drivers still applying</span>
                    <span className="font-mono text-sm text-fg-3">{count(applying)}</span>
                  </button>
                </li>
              )}
            </ul>
          )}
        </Panel>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Panel
          className="xl:col-span-2"
          title={live.length ? `${count(live.length)} live, then most recent` : 'Recent trips'}
          action={
            <Button size="sm" variant="ghost" onClick={() => go('trips')}>
              All trips
            </Button>
          }
          bodyClassName="p-2"
        >
          {recent.length === 0 ? (
            <EmptyState title="No trips yet" body="Trips appear here the moment a rider books." />
          ) : (
            <ul>
              {recent.map((r) => (
                <li key={r.id}>
                  <button type="button" onClick={() => onOpenTrip(r.id)} className="grid w-full cursor-pointer grid-cols-[1fr_auto_auto] items-center gap-4 rounded-xl px-3 py-3 text-left transition-colors hover:bg-sunken">
                    <div className="min-w-0">
                      <p className="truncate text-sm text-fg">
                        {r.pickup_name} <span className="text-fg-3">→</span> {r.dropoff_name}
                      </p>
                      <p className="truncate text-xs text-fg-3">
                        {r.rider_name}
                        {r.driver_name ? ` with ${r.driver_name}` : ''} · {ago(r.created_at)}
                      </p>
                    </div>
                    <span className="font-mono text-sm text-fg tabular-nums">{money(r.fare_cents, r.currency)}</span>
                    <span className="w-36">
                      <StatusTag status={r.status} />
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>
        <Panel
          title="Admin activity"
          action={
            <Button size="sm" variant="ghost" onClick={() => go('activity')}>
              All activity
            </Button>
          }
        >
          {activity.length === 0 ? (
            <p className="text-sm text-fg-3">Approvals, role changes and other admin actions appear here.</p>
          ) : (
            <ol className="space-y-4">
              {activity.slice(0, 6).map((a) => (
                <li key={a.id}>
                  <p className="text-sm text-fg">{a.summary}</p>
                  <p className="text-xs text-fg-3">
                    {a.admin_name} · {ago(a.created_at)}
                  </p>
                </li>
              ))}
            </ol>
          )}
        </Panel>
      </div>
    </div>
  )
}

function Stat({ label, value, foot }: { label: string; value: string; foot: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-line bg-surface p-5">
      <p className="text-[13px] text-fg-2">{label}</p>
      <p className="mt-2 font-mono text-[28px] leading-9 font-medium text-fg tabular-nums">{value}</p>
      <div className="mt-2 text-[13px]">{foot}</div>
    </div>
  )
}

function Delta({ label, tone }: { label: string; tone: 'good' | 'bad' | 'flat' }) {
  return <span className={tone === 'good' ? 'text-good' : tone === 'bad' ? 'text-bad' : 'text-fg-2'}>{label}</span>
}
