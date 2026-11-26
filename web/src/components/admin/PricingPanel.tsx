import { useEffect, useState } from 'react'
import { type AreaPricing, type AreaRules, type RateCard, novaAdminApi } from '../../lib/api'
import { formatError } from '../../lib/errors'
import { ago, money } from '../../lib/format'
import { Button, ConfirmAction, Field, SidePanel, Tag, TextInput, notify } from './ui'

/** Amounts are edited in major units (₹30.00), stored in minor units (3000). Times in minutes. */
const toMajor = (cents: number) => (cents / 100).toFixed(2)
const toMinor = (major: string) => Math.round(Number(major) * 100)
const toMin = (secs: number) => String(Math.round((secs / 60) * 10) / 10)
const toSecs = (min: string) => Math.round(Number(min) * 60)

type CardDraft = Record<keyof Omit<RateCard, 'product_id' | 'product_name' | 'active' | 'updated_at'>, string>

const MONEY_FIELDS: [keyof CardDraft, string][] = [
  ['base_cents', 'Base fare'],
  ['per_km_cents', 'Per km'],
  ['per_min_cents', 'Per minute'],
  ['minimum_cents', 'Minimum fare'],
  ['booking_fee_cents', 'Booking fee'],
  ['wait_per_min_cents', 'Wait, per minute'],
  ['cancel_fee_cents', 'Cancellation fee'],
]
const TIME_FIELDS: [keyof CardDraft, string, string][] = [
  ['wait_grace_secs', 'Free wait', 'After the driver arrives'],
  ['cancel_grace_secs', 'Free cancellation', 'After a driver accepts'],
  ['no_show_wait_secs', 'No-show wait', 'Before the driver may leave'],
  ['long_pickup_after_secs', 'Long pickup after', 'Nova pays the driver beyond this'],
]

function draftOf(card: RateCard): CardDraft {
  const d = {} as CardDraft
  for (const [k] of MONEY_FIELDS) d[k] = toMajor(card[k] as number)
  for (const [k] of TIME_FIELDS) d[k] = toMin(card[k] as number)
  return d
}

function cardOf(card: RateCard, d: CardDraft): RateCard {
  const out = { ...card }
  for (const [k] of MONEY_FIELDS) (out[k] as number) = toMinor(d[k])
  for (const [k] of TIME_FIELDS) (out[k] as number) = toSecs(d[k])
  return out
}

const validDraft = (d: CardDraft) => Object.values(d).every((v) => v.trim() !== '' && Number.isFinite(Number(v)) && Number(v) >= 0)

export function PricingPanel({ areaId, onClose }: { areaId: string | null; onClose: () => void }) {
  const [pricing, setPricing] = useState<AreaPricing | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    setPricing(null)
    setError(null)
    if (areaId)
      novaAdminApi
        .getAreaPricing(areaId)
        .then(setPricing)
        .catch((e) => setError(formatError(e)))
  }, [areaId])

  return (
    <SidePanel
      open={areaId !== null}
      onOpenChange={(o) => !o && onClose()}
      eyebrow="Pricing"
      title={pricing ? pricing.name : 'Loading…'}
      meta={pricing && `Prices in ${pricing.currency}. Changes apply to new quotes; booked trips keep their price.`}
      width="max-w-2xl"
    >
      {error && (
        <p role="alert" className="text-sm text-bad">
          {error}
        </p>
      )}
      {pricing && (
        <div className="space-y-8">
          <RulesForm pricing={pricing} onSaved={setPricing} />
          {pricing.rates.map((r) => (
            <RateForm key={r.product_id} areaId={pricing.id} currency={pricing.currency} card={r} onSaved={setPricing} />
          ))}
        </div>
      )}
    </SidePanel>
  )
}

function RulesForm({ pricing, onSaved }: { pricing: AreaPricing; onSaved: (p: AreaPricing) => void }) {
  const r = pricing.rules
  const initial = {
    max_surge: String(r.max_surge),
    service_fee: String(r.service_fee_bps / 100),
    cancel_cap: String(r.cancel_fee_max_bps / 100),
    rounding: toMajor(r.fare_rounding_minor),
    arrive: String(r.arrive_radius_m),
    dropoff: String(r.dropoff_radius_m),
    reprice: String(r.reprice_over_pct),
  }
  const [d, setD] = useState(initial)
  const [confirming, setConfirming] = useState(false)
  useEffect(() => setD(initial), [pricing]) // eslint-disable-line react-hooks/exhaustive-deps
  const changed = JSON.stringify(d) !== JSON.stringify(initial)
  const rules: AreaRules = {
    max_surge: Number(d.max_surge),
    service_fee_bps: Math.round(Number(d.service_fee) * 100),
    cancel_fee_max_bps: Math.round(Number(d.cancel_cap) * 100),
    fare_rounding_minor: Math.max(1, toMinor(d.rounding)),
    arrive_radius_m: Math.round(Number(d.arrive)),
    dropoff_radius_m: Math.round(Number(d.dropoff)),
    reprice_over_pct: Math.round(Number(d.reprice)),
  }
  const field = (key: keyof typeof d, label: string, hint: string, suffix: string) => (
    <Field label={label} htmlFor={`rule-${key}`} hint={hint}>
      <div className="flex items-center gap-2">
        <TextInput id={`rule-${key}`} inputMode="decimal" className="w-28 font-mono tabular-nums" value={d[key]} onChange={(e) => setD({ ...d, [key]: e.target.value })} />
        <span className="text-sm text-fg-3">{suffix}</span>
      </div>
    </Field>
  )
  return (
    <section aria-labelledby="rules-title" className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h3 id="rules-title" className="text-sm font-medium text-fg">
          Market rules
        </h3>
        <Button size="sm" variant="primary" disabled={!changed} onClick={() => setConfirming(true)}>
          Save rules
        </Button>
      </div>
      <div className="grid grid-cols-1 gap-x-6 gap-y-4 rounded-xl border border-line p-5 sm:grid-cols-2">
        {field('max_surge', 'Surge cap', 'Highest busy-time multiplier. India allows 2×.', '×')}
        {field('service_fee', 'Service fee', 'Nova’s share of the trip fare. The driver keeps the rest.', '%')}
        {field('cancel_cap', 'Cancellation fee cap', 'Share of the fare a cancellation can cost. India: 10%.', '%')}
        {field('rounding', 'Round fares to', 'Prices end on this step, like ₹1 or $0.10.', pricing.currency)}
        {field('arrive', 'Arrival radius', 'Driver must be this close to tap Arrived.', 'm')}
        {field('dropoff', 'Drop-off radius', 'Ending further away reprices on actual trip.', 'm')}
        {field('reprice', 'Reprice when longer by', 'Trips this much longer or slower are repriced.', '%')}
      </div>
      <ConfirmAction
        open={confirming}
        onOpenChange={setConfirming}
        title={`Change market rules in ${pricing.name}?`}
        description="New quotes use these rules straight away. Trips already booked keep the rules they were booked with."
        confirmLabel="Save rules"
        reason={{ label: 'Reason', required: true, placeholder: 'e.g. State notification of 1 July' }}
        formatError={formatError}
        onConfirm={async (reason) => {
          onSaved(await novaAdminApi.updateAreaRules(pricing.id, rules, reason))
          notify.done('Market rules saved')
        }}
      />
    </section>
  )
}

function RateForm({ areaId, currency, card, onSaved }: { areaId: string; currency: string; card: RateCard; onSaved: (p: AreaPricing) => void }) {
  const [d, setD] = useState(() => draftOf(card))
  const [confirming, setConfirming] = useState(false)
  useEffect(() => setD(draftOf(card)), [card])
  const changed = JSON.stringify(d) !== JSON.stringify(draftOf(card))
  const valid = validDraft(d)
  // A worked example so the admin sees what a change means for riders: 10 km, 20 min.
  const example = valid ? cardOf(card, d) : card
  const exampleFare = Math.max(example.minimum_cents, example.base_cents + example.per_km_cents * 10 + example.per_min_cents * 20 + example.booking_fee_cents)
  const id = `rate-${card.product_id}`
  return (
    <section aria-labelledby={id} className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <h3 id={id} className="text-sm font-medium text-fg">
            {card.product_name}
          </h3>
          {!card.active && <Tag>Off</Tag>}
          <span className="text-xs text-fg-3">changed {ago(card.updated_at)}</span>
        </div>
        <Button size="sm" variant="primary" disabled={!changed || !valid} onClick={() => setConfirming(true)}>
          Save {card.product_name}
        </Button>
      </div>
      <div className="space-y-5 rounded-xl border border-line p-5">
        <div className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-4">
          {MONEY_FIELDS.map(([k, label]) => (
            <Field key={k} label={label} htmlFor={`${id}-${k}`}>
              <TextInput id={`${id}-${k}`} inputMode="decimal" className="w-full font-mono tabular-nums" value={d[k]} onChange={(e) => setD({ ...d, [k]: e.target.value })} />
            </Field>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-4">
          {TIME_FIELDS.map(([k, label, hint]) => (
            <Field key={k} label={`${label}, min`} htmlFor={`${id}-${k}`} hint={hint}>
              <TextInput id={`${id}-${k}`} inputMode="decimal" className="w-full font-mono tabular-nums" value={d[k]} onChange={(e) => setD({ ...d, [k]: e.target.value })} />
            </Field>
          ))}
        </div>
        <p className="text-xs text-fg-3">
          A 10 km, 20 minute trip costs <span className="font-mono text-fg-2">{money(exampleFare, currency)}</span> before surge.
        </p>
      </div>
      <ConfirmAction
        open={confirming}
        onOpenChange={setConfirming}
        title={`Change ${card.product_name} prices?`}
        description={`New quotes use these prices straight away. A 10 km, 20 minute trip will cost ${money(exampleFare, currency)} before surge. Trips already booked keep their price.`}
        confirmLabel="Save prices"
        reason={{ label: 'Reason', required: true, placeholder: 'e.g. Match the state-notified fare' }}
        formatError={formatError}
        onConfirm={async (reason) => {
          onSaved(await novaAdminApi.updateRateCard(areaId, cardOf(card, d), reason))
          notify.done(`${card.product_name} prices saved`)
        }}
      />
    </section>
  )
}
