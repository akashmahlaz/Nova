/** One number format across the console: NZ$1,284.50, 12,480, 3.2 km, 14 min. */

const moneyCache = new Map<string, Intl.NumberFormat>()

export function money(cents: number, currency = 'NZD'): string {
  let f = moneyCache.get(currency)
  if (!f) {
    f = new Intl.NumberFormat('en-NZ', { style: 'currency', currency, currencyDisplay: 'narrowSymbol', minimumFractionDigits: 2 })
    moneyCache.set(currency, f)
  }
  return f.format(cents / 100)
}

const intFmt = new Intl.NumberFormat('en-NZ')
export const count = (n: number) => intFmt.format(n)

export function km(meters: number): string {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`
}

export function minutes(seconds: number): string {
  const m = Math.max(1, Math.round(seconds / 60))
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60} min`
}

export function bytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

const dateFmt = new Intl.DateTimeFormat('en-NZ', { day: 'numeric', month: 'short', year: 'numeric' })
const dateTimeFmt = new Intl.DateTimeFormat('en-NZ', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
const timeFmt = new Intl.DateTimeFormat('en-NZ', { hour: 'numeric', minute: '2-digit' })
const longDayFmt = new Intl.DateTimeFormat('en-NZ', { weekday: 'long', day: 'numeric', month: 'long' })

export const date = (iso: string) => dateFmt.format(new Date(iso))
export const dateTime = (iso: string) => dateTimeFmt.format(new Date(iso))
export const time = (iso: string) => timeFmt.format(new Date(iso))
export const longDay = (d: Date = new Date()) => longDayFmt.format(d)

/** "just now", "4 min ago", "3 h ago", then a date. */
export function ago(iso: string, now = Date.now()): string {
  const s = Math.round((now - new Date(iso).getTime()) / 1000)
  if (s < 45) return 'just now'
  if (s < 3600) return `${Math.round(s / 60)} min ago`
  if (s < 86400) return `${Math.round(s / 3600)} h ago`
  if (s < 86400 * 7) return `${Math.round(s / 86400)} d ago`
  return date(iso)
}

/** Change against a previous period, with what it compares to. */
export function change(current: number, previous: number, what = 'last week'): { label: string; tone: 'good' | 'bad' | 'flat' } {
  if (previous === 0 && current === 0) return { label: `No change vs ${what}`, tone: 'flat' }
  if (previous === 0) return { label: `New vs ${what}`, tone: 'good' }
  const pct = Math.round(((current - previous) / previous) * 100)
  if (pct === 0) return { label: `Flat vs ${what}`, tone: 'flat' }
  return { label: `${pct > 0 ? '+' : ''}${pct}% vs ${what}`, tone: pct > 0 ? 'good' : 'bad' }
}

export const fullName = (p: { first_name: string; last_name: string }) => `${p.first_name} ${p.last_name}`.trim()

export const initials = (p: { first_name: string; last_name: string }) =>
  `${p.first_name.charAt(0)}${p.last_name.charAt(0)}`.toUpperCase() || '·'

const PRODUCT_NAMES: Record<string, string> = { go: 'Go', comfort: 'Comfort', xl: 'XL', ev: 'Electric', access: 'Access' }
export const productName = (id: string) => PRODUCT_NAMES[id] ?? id.charAt(0).toUpperCase() + id.slice(1)

const PAYMENT: Record<string, string> = { card: 'Card', cash: 'Cash', wallet: 'Wallet', apple_pay: 'Apple Pay', google_pay: 'Google Pay' }
export const paymentName = (id: string) => PAYMENT[id] ?? id.replace(/_/g, ' ')
