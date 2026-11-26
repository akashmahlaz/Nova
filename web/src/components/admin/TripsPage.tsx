import { useEffect, useMemo, useState } from 'react'
import { type AdminRide, type AdminRideDetail, novaAdminApi } from '../../lib/api'
import { formatError } from '../../lib/errors'
import { ago, count, dateTime, km, minutes, money, paymentName, productName, time } from '../../lib/format'
import { EmptyState, PageHeader, Panel, Plate, ROW, SearchInput, Segmented, SidePanel, StatusTag, TABLE, TD, TH, isLiveTrip, rowProps } from './ui'
import { splitVehicle } from './DriversPage'

type Filter = 'all' | 'live' | 'scheduled' | 'completed' | 'cancelled'

const matches = (r: AdminRide, f: Filter) =>
  f === 'all' ||
  (f === 'live' && isLiveTrip(r.status)) ||
  (f === 'scheduled' && r.status === 'scheduled') ||
  (f === 'completed' && r.status === 'completed') ||
  (f === 'cancelled' && (r.status === 'cancelled' || r.status === 'no_drivers'))

export function TripsPage({ rides, onOpenChange }: { rides: AdminRide[]; onOpenChange: (id: string) => void }) {
  const [filter, setFilter] = useState<Filter>('all')
  const [query, setQuery] = useState('')
  const counts = useMemo(
    () => ({
      live: rides.filter((r) => matches(r, 'live')).length,
      scheduled: rides.filter((r) => matches(r, 'scheduled')).length,
      cancelled: rides.filter((r) => matches(r, 'cancelled')).length,
    }),
    [rides],
  )
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    return rides.filter(
      (r) => matches(r, filter) && (!q || [r.rider_name, r.driver_name ?? '', r.pickup_name, r.dropoff_name, r.service_area_name, r.id].some((s) => s.toLowerCase().includes(q))),
    )
  }, [rides, filter, query])

  return (
    <div className="space-y-6">
      <PageHeader title="Trips" meta={`${count(counts.live)} live now · ${count(counts.scheduled)} scheduled · last ${count(rides.length)} trips`} />
      <Panel
        bodyClassName=""
        title={
          <Segmented
            label="Trip filter"
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: 'all', label: 'All' },
              { value: 'live', label: 'Live', count: counts.live },
              { value: 'scheduled', label: 'Scheduled', count: counts.scheduled },
              { value: 'completed', label: 'Completed' },
              { value: 'cancelled', label: 'Cancelled', count: counts.cancelled },
            ]}
          />
        }
        action={<SearchInput label="Search trips" value={query} onChange={setQuery} placeholder="Rider, driver, place" />}
      >
        {rows.length === 0 ? (
          <EmptyState title="No trips match" body={filter === 'live' ? 'Trips appear here the moment a rider books.' : 'Try another filter or search.'} />
        ) : (
          <div className="overflow-x-auto">
            <table className={TABLE}>
              <thead>
                <tr>
                  <th className={TH}>Route</th>
                  <th className={TH}>Rider</th>
                  <th className={TH}>Driver</th>
                  <th className={TH}>Booked</th>
                  <th className={`${TH} text-right`}>Fare</th>
                  <th className={TH}>Status</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id} className={ROW} {...rowProps(() => onOpenChange(r.id), `Trip from ${r.pickup_name} to ${r.dropoff_name}`)}>
                    <td className={`${TD} max-w-[340px]`}>
                      <div className="truncate font-medium text-fg">
                        {r.pickup_name} <span className="text-fg-3">→</span> {r.dropoff_name}
                      </div>
                      <div className="text-xs text-fg-3">
                        {productName(r.product_id)} · {km(r.distance_meters)} · {r.service_area_name}
                      </div>
                    </td>
                    <td className={`${TD} text-fg-2`}>{r.rider_name}</td>
                    <td className={`${TD} text-fg-2`}>{r.driver_name ?? <span className="text-fg-3">Not assigned</span>}</td>
                    <td className={`${TD} whitespace-nowrap text-fg-2`}>{ago(r.created_at)}</td>
                    <td className={`${TD} text-right font-mono whitespace-nowrap text-fg tabular-nums`}>{money(r.fare_cents, r.currency)}</td>
                    <td className={TD}>
                      <StatusTag status={r.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}

export function TripDetail({ rideId, onClose }: { rideId: string | null; onClose: () => void }) {
  const [ride, setRide] = useState<AdminRideDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    setRide(null)
    setError(null)
    if (rideId)
      novaAdminApi
        .getRide(rideId)
        .then(setRide)
        .catch((e) => setError(formatError(e)))
  }, [rideId])

  const steps = ride
    ? ([
        ['Booked', ride.created_at],
        ride.scheduled_at && ['Scheduled for', ride.scheduled_at],
        ['Driver accepted', ride.accepted_at],
        ['Driver at pickup', ride.arrived_at],
        ['Trip started', ride.started_at],
        ride.status === 'completed' ? ['Dropped off', ride.completed_at] : ride.ended_at && [ride.status === 'cancelled' ? 'Cancelled' : 'Ended', ride.ended_at],
      ].filter(Boolean) as [string, string | null][])
    : []
  const vehicle = splitVehicle(ride?.vehicle ?? null)

  return (
    <SidePanel
      open={rideId !== null}
      onOpenChange={(o) => !o && onClose()}
      eyebrow="Trip"
      title={ride ? `${ride.pickup_name} → ${ride.dropoff_name}` : 'Loading…'}
      meta={ride && <StatusTag status={ride.status} />}
    >
      {error && (
        <p role="alert" className="text-sm text-bad">
          {error}
        </p>
      )}
      {ride && (
        <div className="space-y-8">
          <div className="grid grid-cols-3 gap-4 rounded-xl border border-line p-5">
            <Figure label="Fare" value={money(ride.fare_cents, ride.currency)} />
            <Figure label="Distance" value={km(ride.distance_meters)} />
            <Figure label="Estimated time" value={minutes(ride.duration_seconds)} />
          </div>

          <section className="space-y-3" aria-labelledby="trip-timeline">
            <h3 id="trip-timeline" className="text-sm font-medium text-fg">
              Timeline
            </h3>
            <ol className="relative ml-1.5 space-y-4 border-l border-line pl-5">
              {steps.map(([label, at]) => (
                <li key={label} className="relative">
                  <span aria-hidden="true" className={`absolute top-1.5 -left-[25px] size-2 rounded-full ring-4 ring-surface ${at ? 'bg-fg' : 'bg-line-strong'}`} />
                  <p className={`text-sm ${at ? 'text-fg' : 'text-fg-3'}`}>{label}</p>
                  <p className="font-mono text-xs text-fg-3">{at ? `${time(at)} · ${dateTime(at)}` : 'Not yet'}</p>
                </li>
              ))}
            </ol>
            {ride.cancel_reason && <p className="text-sm text-fg-2">Reason: {ride.cancel_reason.replace(/_/g, ' ')}</p>}
            <p className="text-xs text-fg-3">
              Offered to {count(ride.offers_sent)} {ride.offers_sent === 1 ? 'driver' : 'drivers'}
              {ride.offers_declined > 0 && ` · ${count(ride.offers_declined)} passed or timed out`}
            </p>
          </section>

          <section className="space-y-3" aria-labelledby="trip-people">
            <h3 id="trip-people" className="text-sm font-medium text-fg">
              People and car
            </h3>
            <dl className="grid grid-cols-1 gap-x-8 gap-y-4 rounded-xl border border-line p-5 sm:grid-cols-2">
              <Item label="Rider" value={ride.rider_name} sub={ride.rider_phone} />
              {ride.passenger_name && <Item label="Booked for" value={ride.passenger_name} />}
              <Item label="Driver" value={ride.driver_name ?? 'Not assigned'} sub={ride.driver_phone} />
              <Item
                label="Car"
                value={
                  vehicle.plate ? (
                    <span className="flex flex-wrap items-center gap-2">
                      <Plate plate={vehicle.plate} /> {vehicle.car}
                    </span>
                  ) : (
                    '—'
                  )
                }
              />
            </dl>
          </section>

          <section className="space-y-3" aria-labelledby="trip-route">
            <h3 id="trip-route" className="text-sm font-medium text-fg">
              Route and payment
            </h3>
            <dl className="grid grid-cols-1 gap-x-8 gap-y-4 rounded-xl border border-line p-5 sm:grid-cols-2">
              <Item label="Pickup" value={ride.pickup_name} sub={ride.pickup_address} />
              <Item label="Drop-off" value={ride.dropoff_name} sub={ride.dropoff_address} />
              <Item label="Ride type" value={productName(ride.product_id)} sub={ride.service_area_name} />
              <Item label="Payment" value={paymentName(ride.payment_method)} sub={ride.surge_multiplier > 1 ? `${ride.surge_multiplier.toFixed(1)}× busy pricing` : undefined} />
            </dl>
            <p className="font-mono text-xs text-fg-3">Trip ID {ride.id}</p>
          </section>
        </div>
      )}
    </SidePanel>
  )
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-fg-3">{label}</p>
      <p className="mt-1 font-mono text-lg text-fg tabular-nums">{value}</p>
    </div>
  )
}

function Item({ label, value, sub }: { label: string; value: React.ReactNode; sub?: string | null }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-fg-3">{label}</dt>
      <dd className="mt-1 text-sm text-fg">{value}</dd>
      {sub && <dd className="font-mono text-xs text-fg-3">{sub}</dd>}
    </div>
  )
}
