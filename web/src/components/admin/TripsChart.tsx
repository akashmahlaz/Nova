import { useMemo, useState } from 'react'
import type { DailyRidePoint } from '../../lib/api'
import { change, count, money } from '../../lib/format'
import { Segmented } from './ui'

type Metric = 'trips' | 'fares'
/** dashboard-01's ranges. The console loads 180 days, so each range has a full period before it. */
type Range = '7' | '30' | '90'
const RANGES: { value: Range; label: string }[] = [
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
]

const W = 720
const H = 240
const PAD = { top: 16, right: 16, bottom: 28, left: 48 }

/**
 * The overview's one rich moment, shaped like dashboard-01's interactive area chart: a range
 * and a measure to pick, a Volt area, and a readout that follows the pointer or keyboard focus.
 * The same number of days before the range sits behind it as a dashed line.
 */
export function TripsChart({ data, currency = 'NZD' }: { data: DailyRidePoint[]; currency?: string }) {
  const [metric, setMetric] = useState<Metric>('trips')
  const [range, setRange] = useState<Range>('7')
  const [active, setActive] = useState<number | null>(null)

  const days = Number(range)
  const { shownPts, before } = useMemo(() => ({ shownPts: data.slice(-days), before: data.slice(-2 * days, -days) }), [data, days])

  const value = (p: DailyRidePoint | undefined) => (p ? (metric === 'trips' ? p.completed : p.fare_cents) : 0)
  const fmt = (v: number) => (metric === 'trips' ? count(v) : money(v, currency))
  const total = (pts: DailyRidePoint[]) => pts.reduce((a, p) => a + value(p), 0)

  const n = Math.max(1, shownPts.length - 1)
  const max = Math.max(1, ...shownPts.map(value), ...before.map(value))
  const niceMax = niceCeil(max)
  const x = (i: number) => PAD.left + (i * (W - PAD.left - PAD.right)) / n
  const y = (v: number) => PAD.top + (1 - v / niceMax) * (H - PAD.top - PAD.bottom)
  const line = (pts: DailyRidePoint[]) => smooth(pts.map((p, i) => [x(i), y(value(p))]))
  const ticks = [0, niceMax / 2, niceMax]
  const slot = (W - PAD.left - PAD.right) / n

  const sumShown = total(shownPts)
  const priorLabel = `the ${days} days before`
  const delta = change(sumShown, total(before), priorLabel)
  const shown = active !== null ? shownPts[active] : undefined
  const comparing = shownPts.length > 0 && before.length === shownPts.length
  const labelEvery = range === '7' ? 1 : range === '30' ? 5 : 15
  const lastLabel = RANGES.find((r) => r.value === range)!.label
  const title = metric === 'trips' ? 'Completed trips' : 'Fares from completed trips'

  return (
    <section className="rounded-2xl border border-line bg-surface" aria-labelledby="trips-chart-title">
      <header className="flex flex-wrap items-start justify-between gap-4 px-5 pt-5">
        <div>
          <h2 id="trips-chart-title" className="text-sm font-medium text-fg">
            {title}
          </h2>
          <p className="mt-2 font-mono text-[32px] leading-10 font-medium text-fg tabular-nums">{fmt(shown ? value(shown) : sumShown)}</p>
          <p className="text-[13px] text-fg-2" aria-live="polite">
            {shown ? (
              <>
                {weekday(shown.date)}
                {comparing && ` · ${fmt(value(before[active ?? 0]))} ${days === 7 ? 'the week before' : `${days} days earlier`}`}
              </>
            ) : comparing ? (
              <>
                {lastLabel} · <span className={delta.tone === 'good' ? 'text-good' : delta.tone === 'bad' ? 'text-bad' : 'text-fg-2'}>{delta.label}</span>
              </>
            ) : (
              `Last ${shownPts.length} days`
            )}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Segmented
            label="Chart range"
            value={range}
            onValueChange={(r) => {
              setRange(r)
              setActive(null)
            }}
            options={RANGES}
          />
          <Segmented
            label="Chart measure"
            value={metric}
            onValueChange={setMetric}
            options={[
              { value: 'trips', label: 'Trips' },
              { value: 'fares', label: 'Fares' },
            ]}
          />
        </div>
      </header>

      <div className="px-3 pt-2 pb-3">
        <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label={`${title} per day, last ${shownPts.length} days${comparing ? ` against ${priorLabel}` : ''}`}>
          <defs>
            <linearGradient id="trips-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="var(--volt)" stopOpacity={0.45} />
              <stop offset="95%" stopColor="var(--volt)" stopOpacity={0.04} />
            </linearGradient>
          </defs>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} stroke="var(--line)" />
              <text x={PAD.left - 10} y={y(t) + 4} textAnchor="end" className="fill-fg-3 font-mono text-[11px]">
                {metric === 'trips' ? count(t) : compactMoney(t, currency)}
              </text>
            </g>
          ))}
          {comparing && <path d={line(before)} fill="none" stroke="var(--chart-muted)" strokeWidth={2} strokeDasharray="4 5" strokeLinecap="round" />}
          {shownPts.length > 1 && <path d={`${line(shownPts)} L ${x(shownPts.length - 1)} ${y(0)} L ${x(0)} ${y(0)} Z`} fill="url(#trips-fill)" />}
          <path d={line(shownPts)} fill="none" stroke="var(--volt)" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" />
          <path d={line(shownPts)} fill="none" stroke="var(--on-volt)" strokeOpacity={0.35} strokeWidth={1} strokeLinecap="round" strokeLinejoin="round" />
          {shownPts.map((p, i) => (
            <g key={p.date}>
              {active === i && <line x1={x(i)} x2={x(i)} y1={PAD.top} y2={y(0)} stroke="var(--line-strong)" />}
              {(range === '7' || active === i) && (
                <circle cx={x(i)} cy={y(value(p))} r={active === i ? 5 : 3} fill="var(--surface)" stroke="var(--fg)" strokeWidth={active === i ? 2 : 1.5} />
              )}
              {((shownPts.length - 1 - i) % labelEvery === 0 || active === i) && (
                <text x={x(i)} y={H - 8} textAnchor="middle" className={`font-mono text-[11px] ${active === i ? 'fill-fg' : 'fill-fg-3'}`}>
                  {range === '7' ? weekdayShort(p.date) : dayMonth(p.date)}
                </text>
              )}
              <rect
                x={x(i) - slot / 2}
                y={PAD.top}
                width={slot}
                height={H - PAD.top - PAD.bottom}
                fill="transparent"
                tabIndex={0}
                aria-label={`${weekday(p.date)}: ${fmt(value(p))}${comparing ? `, ${days} days earlier ${fmt(value(before[i]))}` : ''}`}
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
            {lastLabel}
          </span>
          {comparing && (
            <span className="inline-flex items-center gap-2">
              <span aria-hidden="true" className="h-0 w-4 border-t-2 border-dashed border-[var(--chart-muted)]" />
              {priorLabel.charAt(0).toUpperCase() + priorLabel.slice(1)}
            </span>
          )}
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

const compactCache = new Map<string, Intl.NumberFormat>()
function compactMoney(cents: number, currency: string) {
  let f = compactCache.get(currency)
  if (!f) {
    f = new Intl.NumberFormat('en-NZ', { style: 'currency', currency, currencyDisplay: 'narrowSymbol', notation: 'compact', maximumFractionDigits: 1 })
    compactCache.set(currency, f)
  }
  return f.format(cents / 100)
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
const dm = new Intl.DateTimeFormat('en-NZ', { day: 'numeric', month: 'short' })
const asDate = (iso: string) => new Date(`${iso}T12:00:00`)
const weekday = (iso: string) => wdLong.format(asDate(iso))
const weekdayShort = (iso: string) => wdShort.format(asDate(iso))
const dayMonth = (iso: string) => dm.format(asDate(iso))
