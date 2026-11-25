import { useState } from 'react'
import { NovaCard } from './ui'
import type { DailyRidePoint } from '../../lib/api'

export function TelemetryChart({ data }: { data: DailyRidePoint[] }) {
  const [hoverIdx, setHoverIdx] = useState<number | null>(null)
  const maxTotal = Math.max(1, ...data.map((d) => d.total))
  const totalWeekRides = data.reduce((acc, d) => acc + d.total, 0)
  const totalWeekFare = data.reduce((acc, d) => acc + d.fare_cents, 0)
  const activePoint = hoverIdx !== null ? data[hoverIdx] : null

  return (
    <NovaCard className="p-6 flex flex-col justify-between gap-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
            7-Day Dispatch Telemetry
          </div>
          <div className="mt-1 flex items-baseline gap-3">
            <span className="text-2xl font-bold tracking-tight tabular-nums text-zinc-950 dark:text-zinc-50">
              {activePoint ? `${activePoint.total} rides` : `${totalWeekRides} rides`}
            </span>
            <span className="text-xs font-medium tabular-nums text-teal-700 dark:text-teal-400">
              NZ${((activePoint ? activePoint.fare_cents : totalWeekFare) / 100).toFixed(2)}{' '}
              {activePoint ? `on ${activePoint.day}` : 'gross 7d'}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-4 text-[11px] font-medium text-zinc-500 dark:text-zinc-400">
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-xs bg-teal-600 dark:bg-teal-400" />
            Completed
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-xs bg-zinc-200 dark:bg-zinc-700" />
            Requested
          </span>
        </div>
      </div>

      <div className="h-48 flex items-end gap-2 pt-4 border-t border-zinc-200/80 dark:border-zinc-800">
        {data.map((point, idx) => {
          const totalPct = Math.max(6, Math.round((point.total / maxTotal) * 100))
          const isHovered = hoverIdx === idx
          return (
            <div
              key={point.day}
              onMouseEnter={() => setHoverIdx(idx)}
              onMouseLeave={() => setHoverIdx(null)}
              className="flex-1 h-full flex flex-col items-center justify-end gap-2 cursor-pointer"
            >
              <div className="w-full flex-1 flex items-end justify-center relative">
                <div
                  className={`w-full max-w-[42px] rounded-t-md transition-all duration-150 relative overflow-hidden ${
                    isHovered
                      ? 'bg-zinc-300 dark:bg-zinc-600'
                      : 'bg-zinc-200/80 dark:bg-zinc-800'
                  }`}
                  style={{ height: `${totalPct}%` }}
                >
                  <div
                    className={`absolute bottom-0 inset-x-0 rounded-t-md transition-all duration-150 ${
                      isHovered
                        ? 'bg-teal-700 dark:bg-teal-300'
                        : 'bg-teal-600 dark:bg-teal-500'
                    }`}
                    style={{
                      height: `${
                        point.total > 0 ? Math.round((point.completed / point.total) * 100) : 0
                      }%`,
                    }}
                  />
                </div>
              </div>
              <span
                className={`text-[11px] tabular-nums transition-colors ${
                  isHovered
                    ? 'font-semibold text-zinc-950 dark:text-zinc-50'
                    : 'text-zinc-500 dark:text-zinc-400'
                }`}
              >
                {point.day}
              </span>
            </div>
          )
        })}
      </div>
    </NovaCard>
  )
}
