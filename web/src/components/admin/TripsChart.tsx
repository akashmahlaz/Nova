import { useMemo, useState } from 'react'
import type { DailyRidePoint } from '../../lib/api'
import { change, count, money } from '../../lib/format'
import { Segmented } from './ui'

type Metric = 'trips' | 'fares'

const W = 720
const H = 240
const PAD = { top: 16, right: 16, bottom: 28, left: 44 }

/**
 * The overview's one rich moment: the last 7 days in Volt over the 7 before them, dashed.
 * Hover or focus a day to read both weeks.
 */
export function TripsChart({ data, currency = 'NZD' }: { data: DailyRidePoint[]; currency?: string }) {
  const [metric, setMetric] = useState<Metric>('trips')
  const [active, setActive] = useState<number | null>(null)

  const { thisWeek, lastWeek } = useMemo(() => {
    const pts = data.slice(-14)
    return { thisWeek: pts.slice(-7), lastWeek: pts.length > 7 ? pts.slice(0, pts.length - 7) : [] }
  }, [data])

  const value = (p: DailyRidePoint | undefined) => (p ? (metric === 'trips' ? p.completed : p.fare_cents) : 0)
  const fmt = (v: number) => (metric === 'trips' ? count(v) : money(v, currency))
  const total = (pts: DailyRidePoint[]) => pts.reduce((a, p) => a + value(p), 0)

  const max = Math.max(1, ...thisWeek.map(value), ...lastWeek.map(value))
  const niceMax = niceCeil(max)
  const x = (i: number) => PAD.left + (i * (W - PAD.left - PAD.right)) / 6
  const y = (v: number) => PAD.top + (1 - v / niceMax) * (H - PAD.top - PAD.bottom)
  const line = (pts: DailyRidePoint[]) => smooth(pts.map((p, i) => [x(i), y(value(p))]))
  const ticks = [0, niceMax / 2, niceMax]

  const sumThis = total(thisWeek)
  const sumLast = total(lastWeek)
  const delta = change(sumThis, sumLast, 'the 7 days before')
  const shown = active !== null ? thisWeek[active] : undefined

  return (
    <section className="rounded-2xl border border-line bg-surface" aria-labelledby="trips-chart-title">
      <header className="flex flex-wrap items-start justify-between gap-4 px-5 pt-5">
        <div>
          <h2 id="trips-chart-title" className="text-sm font-medium text-fg">
            {metric === 'trips' ? 'Completed trips' : 'Fares from completed trips'}
          </h2>
          <p className="mt-2 font-mono text-[32px] leading-10 font-medium text-fg tabular-nums">
            {fmt(shown ? value(shown) : sumThis)}
          </p>
          <p className="text-[13px] text-fg-2">
            {shown ? (
              <>
                {weekday(shown.date)} · {fmt(value(lastWeek[active ?? 0]))} the week before
              </>
            ) : (
              <>
                Last 7 days ·{' '}
                <span className={delta.tone === 'good' ? 'text-good' : delta.tone === 'bad' ? 'text-bad' : 'text-fg-2'}>{delta.label}</span>
              </>
            )}
          </p>
        </div>
        <Segmented
          label="Chart measure"
          value={metric}
          onValueChange={setMetric}
          options={[
            { value: 'trips', label: 'Trips' },
            { value: 'fares', label: 'Fares' },
          ]}
        />
      </header>

      <div className="px-3 pt-2 pb-3">
        <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label={`${metric === 'trips' ? 'Completed trips' : 'Fares'} per day, last 7 days against the 7 days before`}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} stroke="var(--line)" />
              <text x={PAD.left - 10} y={y(t) + 4} textAnchor="end" className="fill-fg-3 font-mono text-[11px]">
                {metric === 'trips' ? count(t) : compactMoney(t)}
              </text>
            </g>
          ))}
          {lastWeek.length === 7 && <path d={line(lastWeek)} fill="none" stroke="var(--chart-muted)" strokeWidth={2} strokeDasharray="4 5" strokeLinecap="round" />}
          <path d={`${line(thisWeek)} L ${x(thisWeek.length - 1)} ${y(0)} L ${x(0)} ${y(0)} Z`} fill="var(--volt)" opacity={0.14} />
          <path d={line(thisWeek)} fill="none" stroke="var(--volt)" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" />
          <path d={line(thisWeek)} fill="none" stroke="var(--on-volt)" strokeOpacity={0.35} strokeWidth={1} strokeLinecap="round" strokeLinejoin="round" className="dark:hidden" />
          {thisWeek.map((p, i) => (
            <g key={p.date}>
              {active === i && <line x1={x(i)} x2={x(i)} y1={PAD.top} y2={y(0)} stroke="var(--line-strong)" />}
              <circle cx={x(i)} cy={y(value(p))} r={active === i ? 5 : 3.5} fill="var(--surface)" stroke="var(--fg)" strokeWidth={active === i ? 2 : 1.5} />
              <text x={x(i)} y={H - 8} textAnchor="middle" className={`font-mono text-[11px] ${active === i ? 'fill-fg' : 'fill-fg-3'}`}>
                {weekdayShort(p.date)}
              </text>
              <rect
                x={x(i) - (W - PAD.left - PAD.right) / 12}
                y={PAD.top}
                width={(W - PAD.left - PAD.right) / 6}
                height={H - PAD.top - PAD.bottom}
                fill="transparent"
                tabIndex={0}
                aria-label={`${weekday(p.date)}: ${fmt(value(p))}, the week before ${fmt(value(lastWeek[i]))}`}
                onMouseEnter={() => setActive(i)}
                onMouseLeave={() => setActive(null)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
                className="cursor-crosshair outline-none"
              />
            </g>
          ))}
        </svg>
        <div className="flex items-center gap-5 px-2 text-xs text-fg-3">
          <span className="inline-flex items-center gap-2">
            <span aria-hidden="true" className="h-[3px] w-4 rounded-full bg-volt" />
            Last 7 days
          </span>
          <span className="inline-flex items-center gap-2">
            <span aria-hidden="true" className="h-0 w-4 border-t-2 border-dashed border-[var(--chart-muted)]" />
            The 7 days before
          </span>
        </div>
      </div>
    </section>
  )
}

function niceCeil(v: number) {
  if (v <= 4) return 4
  const mag = 10 ** Math.floor(Math.log10(v))
  const n = v / mag
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * mag
}

function compactMoney(cents: number) {
  const d = cents / 100
  return d >= 1000 ? `$${(d / 1000).toFixed(d >= 10000 ? 0 : 1)}k` : `$${Math.round(d)}`
}

/** Monotone-ish smoothing: gentle curves that never overshoot below zero. */
function smooth(points: [number, number][]) {
  if (points.length === 0) return ''
  let d = `M ${points[0][0]} ${points[0][1]}`
  for (let i = 1; i < points.length; i++) {
    const [x0, y0] = points[i - 1]
    const [x1, y1] = points[i]
    const cx = (x0 + x1) / 2
    d += ` C ${cx} ${y0}, ${cx} ${y1}, ${x1} ${y1}`
  }
  return d
}

const wdLong = new Intl.DateTimeFormat('en-NZ', { weekday: 'long', day: 'numeric', month: 'short' })
const wdShort = new Intl.DateTimeFormat('en-NZ', { weekday: 'short' })
const asDate = (iso: string) => new Date(`${iso}T12:00:00`)
const weekday = (iso: string) => wdLong.format(asDate(iso))
const weekdayShort = (iso: string) => wdShort.format(asDate(iso))
