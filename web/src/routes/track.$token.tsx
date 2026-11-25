import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'

interface RidePlace {
  name: string
  address: string
  lat: number
  lng: number
}

interface RideStop {
  name: string
  lat: number
  lng: number
}

interface TrackedRide {
  status: string
  product_name: string
  booker_name: string
  passenger_name?: string | null
  pickup: RidePlace
  dropoff: RidePlace
  stops: RideStop[]
  distance_meters: number
  duration_seconds: number
  scheduled_at?: string | null
}

const API_BASE =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
  'https://nova-server-production.punjab.fireclaw.in'

const PLAY_STORE_URL =
  'https://play.google.com/store/apps/details?id=com.nova.kiwiriders'

export const Route = createFileRoute('/track/$token')({
  component: TrackRidePage,
})

function TrackRidePage() {
  const { token } = Route.useParams()
  const [ride, setRide] = useState<TrackedRide | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setInterval> | undefined

    async function load() {
      try {
        const res = await fetch(`${API_BASE}/v1/track/${encodeURIComponent(token)}`)
        if (!res.ok) throw new Error('Ride link not found or expired.')
        const data = (await res.json()) as TrackedRide
        if (!active) return
        setRide(data)
        setError(null)
        if (['completed', 'cancelled', 'no_drivers'].includes(data.status) && timer) {
          clearInterval(timer)
        }
      } catch (err) {
        if (!active) return
        setError(err instanceof Error ? err.message : 'Unable to load ride status.')
      }
    }

    load()
    timer = setInterval(load, 10000)
    return () => {
      active = false
      if (timer) clearInterval(timer)
    }
  }, [token])

  return (
    <main className="min-h-screen bg-[#faf9f7] text-[#191c1e] flex items-center justify-center p-6">
      <div className="w-full max-w-md rounded-2xl border border-black/10 bg-white p-6 space-y-5 shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold tracking-wider uppercase text-black/50">
            Nova Ride Tracking
          </span>
          {ride && (
            <span className="rounded-full bg-[#e8f0fe] text-[#1967d2] px-3 py-1 text-xs font-medium capitalize">
              {ride.status.replace('_', ' ')}
            </span>
          )}
        </div>

        {ride ? (
          <>
            <div>
              <h1 className="text-xl font-semibold">
                {ride.booker_name} booked your {ride.product_name}
              </h1>
              {ride.scheduled_at && (
                <p className="text-sm text-black/60 mt-1">
                  Scheduled for {new Date(ride.scheduled_at).toLocaleString()}
                </p>
              )}
            </div>

            <div className="space-y-3 rounded-xl border border-black/8 bg-[#f7f8fa] p-4 text-sm">
              <div>
                <div className="text-xs text-black/50">Pickup</div>
                <div className="font-medium">{ride.pickup.name}</div>
              </div>
              {ride.stops.map((stop, idx) => (
                <div key={`${stop.name}-${idx}`}>
                  <div className="text-xs text-black/50">Stop {idx + 1}</div>
                  <div className="font-medium">{stop.name}</div>
                </div>
              ))}
              <div>
                <div className="text-xs text-black/50">Drop-off</div>
                <div className="font-medium">{ride.dropoff.name}</div>
              </div>
            </div>
          </>
        ) : error ? (
          <p className="text-sm text-red-600">{error}</p>
        ) : (
          <p className="text-sm text-black/60">Loading live ride details…</p>
        )}

        <div className="pt-2 border-t border-black/8 space-y-2">
          <p className="text-xs text-black/60">
            Get live driver updates and notifications in the Nova app.
          </p>
          <a
            href={PLAY_STORE_URL}
            className="block w-full text-center rounded-xl bg-[#191c1e] text-white py-3 text-sm font-medium hover:bg-black/85 transition-colors"
          >
            Download Nova on Google Play
          </a>
        </div>
      </div>
    </main>
  )
}
