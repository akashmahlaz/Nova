import { useEffect, useMemo, useState } from 'react'
import { type AdminRide, type AdminRideDetail, novaAdminApi } from '../../lib/api'
import { formatError } from '../../lib/errors'
import { ago, count, dateTime, km, minutes, money, paymentName, productName, time } from '../../lib/format'
import { DataTable, PageHeader, Plate, Segmented, SidePanel, StatusTag, isLiveTrip } from './ui'
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
      <DataTable
        id="trips"
        label="Trips"
        tabs={
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
        search={{ value: query, onChange: setQuery, placeholder: 'Rider, driver, place' }}
        rows={rows}
        rowKey={(r) => r.id}
        onRowClick={(r) => onOpenChange(r.id)}
        rowLabel={(r) => `Trip from ${r.pickup_name} to ${r.dropoff_name}`}
        empty={{ title: 'No trips match', body: filter === 'live' ? 'Trips appear here the moment a rider books.' : 'Try another filter or search.' }}
        summary={`${count(rows.length)} ${rows.length === 1 ? 'trip' : 'trips'} shown`}
        columns={[
          {
            id: 'route',
            header: 'Route',
            hideable: false,
            className: 'max-w-[340px]',
            cell: (r) => (
              <>
                <div className="truncate font-medium text-fg">
                  {r.pickup_name} <span className="text-fg-3">→</span> {r.dropoff_name}
                </div>
                <div className="text-xs text-fg-3">
                  {productName(r.product_id)} · {km(r.distance_meters)} · {r.service_area_name}
                </div>
              </>
            ),
          },
          { id: 'rider', header: 'Rider', className: 'text-fg-2', cell: (r) => r.rider_name },
          { id: 'driver', header: 'Driver', className: 'text-fg-2', cell: (r) => r.driver_name ?? <span className="text-fg-3">Not assigned</span> },
          { id: 'payment', header: 'Payment', defaultHidden: true, className: 'text-fg-2', cell: (r) => paymentName(r.payment_method) },
          { id: 'time', header: 'Time', defaultHidden: true, className: 'whitespace-nowrap text-fg-2', cell: (r) => minutes(r.duration_seconds) },
          { id: 'booked', header: 'Booked', className: 'whitespace-nowrap text-fg-2', cell: (r) => ago(r.created_at) },
          { id: 'fare', header: 'Fare', align: 'right', className: 'font-mono whitespace-nowrap text-fg tabular-nums', cell: (r) => money(r.fare_cents, r.currency) },
          { id: 'status', header: 'Status', cell: (r) => <StatusTag status={r.status} /> },
        ]}
      />
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
            <Figure label={ride.status === 'completed' ? 'Rider paid' : 'Upfront price'} value={money(ride.fare_cents, ride.currency)} />
            <Figure label="Distance" value={km(ride.actual_distance_meters ?? ride.distance_meters)} />
            <Figure label={ride.actual_duration_seconds != null ? 'Trip time' : 'Estimated time'} value={minutes(ride.actual_duration_seconds ?? ride.duration_seconds)} />
          </div>

          <FareBreakdown ride={ride} />

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

const REASONS: Record<string, string> = {
  ended_away: 'Ended away from the destination, so the fare is for the distance and time travelled',
  longer_trip: 'Took much longer than the route, so the fare is for the actual distance and time',
}

/** Where the money went: what the rider was shown, what they paid, and the driver's share. */
function FareBreakdown({ ride }: { ride: AdminRideDetail }) {
  const m = (c: number) => money(c, ride.currency)
  const settled = ride.driver_earnings_cents != null
  const rows: [string, string, string?][] = []
  if (ride.upfront_fare_cents != null) rows.push(['Upfront price', m(ride.upfront_fare_cents)])
  if (ride.fare_basis === 'actual') rows.push(['Repriced on actual trip', m(ride.fare_cents - ride.wait_fee_cents), ride.fare_change_reason ? REASONS[ride.fare_change_reason] : undefined])
  if (ride.wait_fee_cents > 0) rows.push(['Wait time', m(ride.wait_fee_cents), ride.wait_seconds != null ? `${minutes(ride.wait_seconds)} at the pickup` : undefined])
  if (ride.cancel_fee_cents > 0) rows.push([ride.cancel_reason === 'rider_no_show' ? 'No-show fee' : 'Cancellation fee', m(ride.cancel_fee_cents)])
  if (ride.booking_fee_cents != null && ride.status === 'completed') rows.push(['Booking fee, to Nova', m(ride.booking_fee_cents)])
  if (ride.service_fee_cents != null) rows.push(['Service fee, to Nova', m(ride.service_fee_cents)])
  if (ride.long_pickup_cents > 0) rows.push(['Long pickup, paid by Nova', m(ride.long_pickup_cents)])
  if (settled) rows.push(['Driver earned', m(ride.driver_earnings_cents!)])
  const checks = [
    ride.arrived_distance_m != null && `Arrived ${ride.arrived_distance_m} m from the pickup`,
    ride.dropoff_distance_m != null && `Ended ${ride.dropoff_distance_m < 1000 ? `${ride.dropoff_distance_m} m` : km(ride.dropoff_distance_m)} from the destination`,
    ride.trail_points > 0 && `${count(ride.trail_points)} GPS points recorded`,
  ].filter(Boolean) as string[]
  if (rows.length <= 1 && checks.length === 0) return null
  return (
    <section className="space-y-3" aria-labelledby="trip-money">
      <h3 id="trip-money" className="text-sm font-medium text-fg">
        Money
      </h3>
      <dl className="divide-y divide-line rounded-xl border border-line">
        {rows.map(([label, value, note]) => (
          <div key={label} className="flex items-start justify-between gap-4 px-5 py-3">
            <dt className="text-sm text-fg-2">
              {label}
              {note && <span className="mt-0.5 block text-xs text-fg-3">{note}</span>}
            </dt>
            <dd className="font-mono text-sm text-fg tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      {checks.length > 0 && <p className="text-xs text-fg-3">{checks.join(' · ')}</p>}
    </section>
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
