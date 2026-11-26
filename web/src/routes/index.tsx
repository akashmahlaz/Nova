import { createFileRoute } from '@tanstack/react-router'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Activity, Car, FolderLock, LayoutDashboard, LogOut, MapPinned, Menu, Navigation, RefreshCw, Search, Users } from 'lucide-react'
import {
  type AdminDriver,
  type AdminMediaItem,
  type AdminOverview,
  type AdminRide,
  type AdminServiceArea,
  type AdminUser,
  ApiError,
  type AuditEntry,
  type Session,
  getStoredSession,
  novaAdminApi,
  saveStoredSession,
} from '../lib/api'
import { formatError } from '../lib/errors'
import { ago, count, fullName, initials, longDay } from '../lib/format'
import { Avatar, Button, Field, IconButton, Kbd, PageHeader, Segmented, SidePanel, TextInput, ThemeSwitch, Toasts } from '../components/admin/ui'
import { Overview } from '../components/admin/Overview'
import { DriverReview, DriversPage } from '../components/admin/DriversPage'
import { TripDetail, TripsPage } from '../components/admin/TripsPage'
import { AccountsPage } from '../components/admin/AccountsPage'
import { FilesPage } from '../components/admin/FilesPage'
import { ActivityPage, AreasPage } from '../components/admin/AreasActivity'
import { Dialog, DialogBackdrop, DialogContent, DialogTitle } from '@ui-sdk/react/dialog'

export const Route = createFileRoute('/')({ component: AdminConsole })

type Page = 'overview' | 'trips' | 'drivers' | 'accounts' | 'areas' | 'files' | 'activity'
const PAGES: Page[] = ['overview', 'trips', 'drivers', 'accounts', 'areas', 'files', 'activity']

function AdminConsole() {
  const [session, setSession] = useState<Session | null>(null)
  const [ready, setReady] = useState(false)
  useEffect(() => {
    setSession(getStoredSession())
    setReady(true)
  }, [])
  if (!ready) return <div className="min-h-screen bg-canvas" />

  if (!session)
    return (
      <SignIn
        onSuccess={(s) => {
          saveStoredSession(s)
          setSession(s)
        }}
      />
    )

  if (session.user.role !== 'admin')
    return (
      <Centered>
        <h1 className="text-xl font-medium text-fg">This console is for Nova admins</h1>
        <p className="text-sm leading-6 text-fg-2">
          You're signed in as {session.user.first_name}, a {session.user.role}. Ask an admin to give your account admin access, then sign in again.
        </p>
        <Button
          variant="solid"
          className="w-full"
          onClick={async () => {
            await novaAdminApi.logout()
            setSession(null)
          }}
        >
          Sign out
        </Button>
      </Centered>
    )

  return (
    <Console
      session={session}
      onSignedOut={() => setSession(null)}
      onLogout={async () => {
        await novaAdminApi.logout()
        setSession(null)
      }}
    />
  )
}

function Wordmark() {
  return (
    <span className="inline-flex items-center gap-2.5">
      <span aria-hidden="true" className="inline-flex size-7 items-center justify-center rounded-lg bg-volt text-[13px] font-semibold text-on-volt">
        N
      </span>
      <span className="text-[15px] font-medium text-fg">
        Nova <span className="text-fg-3">Ops</span>
      </span>
    </span>
  )
}

function Centered({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <header className="flex h-16 items-center justify-between px-6">
        <Wordmark />
        <ThemeSwitch />
      </header>
      <main className="flex flex-1 items-center justify-center px-6 pb-16">
        <div className="w-full max-w-[400px] space-y-5 rounded-2xl border border-line bg-surface p-8">{children}</div>
      </main>
    </div>
  )
}

function SignIn({ onSuccess }: { onSuccess: (s: Session) => void }) {
  const [channel, setChannel] = useState<'email' | 'phone'>('email')
  const [destination, setDestination] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit() {
    const dest = channel === 'email' ? destination.trim().toLowerCase() : destination.replace(/\s+/g, '')
    if (!dest) return
    setBusy(true)
    setError(null)
    try {
      if (!codeSent) {
        await novaAdminApi.sendOtp(channel, dest)
        setCodeSent(true)
      } else {
        onSuccess(await novaAdminApi.verifyOtp(channel, dest, code.trim()))
      }
    } catch (err) {
      setError(formatError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Centered>
      <div className="space-y-1.5">
        <h1 className="text-xl font-medium text-fg">Sign in to Nova Ops</h1>
        <p className="text-sm text-fg-2">{codeSent ? `Enter the 6-digit code we sent to ${destination.trim()}` : "We'll send a one-time code. Admin accounts only."}</p>
      </div>
      {!codeSent && (
        <Segmented
          label="Sign in with"
          value={channel}
          onValueChange={(c) => {
            setChannel(c)
            setError(null)
          }}
          options={[
            { value: 'email', label: 'Email' },
            { value: 'phone', label: 'Mobile' },
          ]}
        />
      )}
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          void submit()
        }}
      >
        {!codeSent ? (
          <Field label={channel === 'email' ? 'Work email' : 'Mobile number'} htmlFor="signin-dest" hint={channel === 'phone' ? 'Include the country code, e.g. +64 21 123 4567' : undefined}>
            <TextInput
              id="signin-dest"
              type={channel === 'email' ? 'email' : 'tel'}
              autoComplete={channel === 'email' ? 'email' : 'tel'}
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
              placeholder={channel === 'email' ? 'you@novataxis.co.nz' : '+64 21 123 4567'}
              className="h-10"
              autoFocus
            />
          </Field>
        ) : (
          <Field label="Code" htmlFor="signin-code">
            <TextInput
              id="signin-code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              className="h-11 font-mono text-lg tracking-[0.4em]"
              autoFocus
            />
          </Field>
        )}
        {error && (
          <p role="alert" className="text-sm text-bad">
            {error}
          </p>
        )}
        <Button type="submit" variant="primary" className="h-10 w-full" disabled={busy || (codeSent ? code.length !== 6 : !destination.trim())}>
          {busy ? 'One moment…' : codeSent ? 'Sign in' : 'Send code'}
        </Button>
        {codeSent && (
          <Button
            type="button"
            variant="ghost"
            className="w-full"
            onClick={() => {
              setCodeSent(false)
              setCode('')
              setError(null)
            }}
          >
            Use a different {channel === 'email' ? 'email' : 'number'}
          </Button>
        )}
      </form>
    </Centered>
  )
}

// ---------- console ----------

type NavItem = { id: Page; label: string; icon: React.ReactNode; count?: number }

function Console({ session, onLogout, onSignedOut }: { session: Session; onLogout: () => Promise<void>; onSignedOut: () => void }) {
  const [page, setPage] = useState<Page>('overview')
  const [navOpen, setNavOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [drivers, setDrivers] = useState<AdminDriver[]>([])
  const [rides, setRides] = useState<AdminRide[]>([])
  const [areas, setAreas] = useState<AdminServiceArea[]>([])
  const [media, setMedia] = useState<AdminMediaItem[]>([])
  const [activity, setActivity] = useState<AuditEntry[]>([])
  const [loadedAt, setLoadedAt] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const [, tick] = useState(0)
  // Record panels open on top of any page, so the overview, search and other pages can open them.
  const [openDriver, setOpenDriver] = useState<string | null>(null)
  const [openTrip, setOpenTrip] = useState<string | null>(null)
  const [openAccount, setOpenAccount] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [ov, us, dr, rd, ar, md, au] = await Promise.all([
        novaAdminApi.getOverview(),
        novaAdminApi.listUsers(),
        novaAdminApi.listDrivers(),
        novaAdminApi.listRides(),
        novaAdminApi.listServiceAreas(),
        novaAdminApi.listMedia(),
        novaAdminApi.listAudit({ limit: 20 }),
      ])
      setOverview(ov)
      setUsers(us)
      setDrivers(dr)
      setRides(rd)
      setAreas(ar)
      setMedia(md)
      setActivity(au)
      setLoadedAt(new Date().toISOString())
      setVersion((v) => v + 1)
      setError(null)
    } catch (err) {
      // The refresh token was rejected: back to sign-in instead of a broken console.
      if (err instanceof ApiError && err.code === 'unauthorized' && !getStoredSession()) return onSignedOut()
      setError(formatError(err))
    } finally {
      setLoading(false)
    }
  }, [onSignedOut])

  useEffect(() => {
    const fromHash = window.location.hash.slice(1) as Page
    if (PAGES.includes(fromHash)) setPage(fromHash)
    void load()
    // Live numbers stay fresh while the console is open and visible.
    const poll = window.setInterval(() => document.visibilityState === 'visible' && void load(), 30_000)
    const clock = window.setInterval(() => tick((t) => t + 1), 15_000)
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setSearchOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => {
      window.clearInterval(poll)
      window.clearInterval(clock)
      window.removeEventListener('keydown', onKey)
    }
  }, [load])

  const go = (p: Page) => {
    setPage(p)
    setNavOpen(false)
    window.history.replaceState(null, '', `#${p}`)
  }

  const reviewCount = drivers.filter((d) => d.status === 'pending' && d.submitted_at && d.docs_ready >= d.docs_required).length
  const groups: { label: string; items: NavItem[] }[] = [
    {
      label: 'Operations',
      items: [
        { id: 'overview', label: 'Overview', icon: <LayoutDashboard className="size-4" /> },
        { id: 'trips', label: 'Trips', icon: <Navigation className="size-4" />, count: overview?.active_rides },
        { id: 'drivers', label: 'Drivers', icon: <Car className="size-4" />, count: reviewCount },
        { id: 'accounts', label: 'Accounts', icon: <Users className="size-4" /> },
      ],
    },
    {
      label: 'Network',
      items: [
        { id: 'areas', label: 'Service areas', icon: <MapPinned className="size-4" /> },
        { id: 'files', label: 'Files', icon: <FolderLock className="size-4" />, count: overview?.media_pending_review },
      ],
    },
    { label: 'Oversight', items: [{ id: 'activity', label: 'Activity', icon: <Activity className="size-4" /> }] },
  ]

  const sidebar = (
    <div className="flex h-full flex-col px-3 pt-4 pb-3">
      <div className="px-2 pb-6">
        <Wordmark />
      </div>
      <nav aria-label="Console" className="flex-1 space-y-6">
        {groups.map((g) => (
          <div key={g.label}>
            <p className="px-2 pb-2 text-xs text-fg-3">{g.label}</p>
            <ul className="space-y-0.5">
              {g.items.map((item) => {
                const active = page === item.id
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => go(item.id)}
                      aria-current={active ? 'page' : undefined}
                      className={`flex h-9 w-full cursor-pointer items-center gap-2.5 rounded-[10px] px-2 text-sm transition-colors ${active ? 'bg-surface font-medium text-fg shadow-[0_1px_2px_rgb(0_0_0/0.05)] ring-1 ring-line' : 'text-fg-2 hover:bg-surface/60 hover:text-fg'}`}
                    >
                      <span className={active ? 'text-fg' : 'text-fg-3'}>{item.icon}</span>
                      <span className="flex-1 text-left">{item.label}</span>
                      {item.count !== undefined && item.count > 0 && <span className="font-mono text-xs text-fg-3">{count(item.count)}</span>}
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </nav>
      <div className="flex items-center gap-3 rounded-xl px-2 py-2">
        <Avatar text={initials(session.user)} size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-fg">{fullName(session.user)}</p>
          <p className="truncate text-xs text-fg-3">{session.user.email ?? session.user.phone_e164}</p>
        </div>
        <IconButton label="Sign out" onClick={() => void onLogout()}>
          <LogOut className="size-4" />
        </IconButton>
      </div>
    </div>
  )

  const currentLabel = groups.flatMap((g) => g.items).find((i) => i.id === page)?.label ?? ''

  return (
    <div className="flex min-h-screen bg-canvas text-fg">
      <aside className="sticky top-0 hidden h-screen w-[248px] shrink-0 lg:block">{sidebar}</aside>
      <SidePanel open={navOpen} onOpenChange={setNavOpen} title="Menu" width="max-w-[280px]">
        <div className="-mx-6 -my-5 h-full">{sidebar}</div>
      </SidePanel>

      <div className="flex min-w-0 flex-1 flex-col lg:py-2 lg:pr-2">
        <div className="flex flex-1 flex-col bg-surface lg:rounded-2xl lg:ring-1 lg:ring-line">
          <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-line bg-surface/95 px-4 backdrop-blur-sm lg:rounded-t-2xl lg:px-8">
            <IconButton label="Open menu" className="lg:hidden" onClick={() => setNavOpen(true)}>
              <Menu className="size-4" />
            </IconButton>
            <span className="text-sm font-medium text-fg lg:hidden">{currentLabel}</span>
            <button
              type="button"
              onClick={() => setSearchOpen(true)}
              className="hidden h-9 w-80 cursor-pointer items-center gap-2 rounded-[10px] border border-line-strong bg-surface px-3 text-sm text-fg-3 transition-colors hover:border-fg-3 md:flex"
            >
              <Search className="size-4" />
              <span className="flex-1 text-left">Search people, cars, trips</span>
              <Kbd>⌘K</Kbd>
            </button>
            <div className="ml-auto flex items-center gap-2">
              <IconButton label="Search" className="md:hidden" onClick={() => setSearchOpen(true)}>
                <Search className="size-4" />
              </IconButton>
              <span className="hidden text-xs text-fg-3 sm:inline" aria-live="polite">
                {loading ? 'Updating…' : loadedAt ? `Updated ${ago(loadedAt)}` : ''}
              </span>
              <IconButton label="Refresh now" disabled={loading} onClick={() => void load()}>
                <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
              </IconButton>
              <ThemeSwitch />
            </div>
          </header>

          <main className="mx-auto w-full max-w-[1480px] flex-1 space-y-6 px-4 py-6 md:px-8 md:py-8">
            {error && (
              <div role="alert" className="flex items-center justify-between gap-4 rounded-xl border border-bad/30 bg-bad/8 px-4 py-3 text-sm text-fg">
                <span>{error}</span>
                <Button size="sm" onClick={() => void load()}>
                  Try again
                </Button>
              </div>
            )}
            {page === 'overview' && (
              <>
                <PageHeader title="Overview" meta={longDay()} />
                <Overview
                  overview={overview}
                  drivers={drivers}
                  rides={rides}
                  media={media}
                  activity={activity}
                  onOpenDriver={setOpenDriver}
                  onOpenTrip={setOpenTrip}
                  go={go}
                />
              </>
            )}
            {page === 'trips' && <TripsPage rides={rides} onOpenChange={setOpenTrip} />}
            {page === 'drivers' && <DriversPage drivers={drivers} onOpenChange={setOpenDriver} />}
            {page === 'accounts' && (
              <AccountsPage
                users={users}
                currentUserId={session.user.id}
                onChanged={load}
                openId={openAccount}
                onOpenChange={setOpenAccount}
                onOpenDriver={(id) => {
                  setOpenAccount(null)
                  setOpenDriver(id)
                }}
              />
            )}
            {page === 'areas' && <AreasPage areas={areas} onChanged={load} />}
            {page === 'files' && <FilesPage media={media} overview={overview} onChanged={load} onOpenDriver={setOpenDriver} />}
            {page === 'activity' && <ActivityPage version={version} />}
          </main>
        </div>
      </div>

      <RecordPanels openDriver={openDriver} setOpenDriver={setOpenDriver} openTrip={openTrip} setOpenTrip={setOpenTrip} onChanged={load} />
      <SearchPalette
        open={searchOpen}
        onOpenChange={setSearchOpen}
        users={users}
        drivers={drivers}
        rides={rides}
        pages={groups.flatMap((g) => g.items)}
        onPick={(r) => {
          setSearchOpen(false)
          if (r.kind === 'page') go(r.id as Page)
          if (r.kind === 'driver') setOpenDriver(r.id)
          if (r.kind === 'trip') setOpenTrip(r.id)
          if (r.kind === 'account') {
            go('accounts')
            setOpenAccount(r.id)
          }
        }}
      />
      <Toasts />
    </div>
  )
}

/** Driver and trip panels live at the top so any page, the overview or search can open them. */
function RecordPanels({
  openDriver,
  setOpenDriver,
  openTrip,
  setOpenTrip,
  onChanged,
}: {
  openDriver: string | null
  setOpenDriver: (id: string | null) => void
  openTrip: string | null
  setOpenTrip: (id: string | null) => void
  onChanged: () => Promise<void>
}) {
  return (
    <>
      <DriverReview userId={openDriver} onClose={() => setOpenDriver(null)} onChanged={onChanged} />
      <TripDetail rideId={openTrip} onClose={() => setOpenTrip(null)} />
    </>
  )
}


// ---------- search ----------

type Result = { kind: 'page' | 'driver' | 'trip' | 'account'; id: string; title: string; sub: string }

function SearchPalette({
  open,
  onOpenChange,
  users,
  drivers,
  rides,
  pages,
  onPick,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  users: AdminUser[]
  drivers: AdminDriver[]
  rides: AdminRide[]
  pages: NavItem[]
  onPick: (r: Result) => void
}) {
  const [q, setQ] = useState('')
  const [index, setIndex] = useState(0)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (open) {
      setQ('')
      setIndex(0)
      window.setTimeout(() => input.current?.focus(), 30)
    }
  }, [open])

  const results = useMemo<Result[]>(() => {
    const s = q.trim().toLowerCase()
    const pageHits: Result[] = pages.filter((p) => !s || p.label.toLowerCase().includes(s)).map((p) => ({ kind: 'page', id: p.id, title: p.label, sub: 'Go to page' }))
    if (!s) return pageHits
    const has = (...xs: (string | null | undefined)[]) => xs.some((x) => x?.toLowerCase().includes(s))
    const driverHits: Result[] = drivers
      .filter((d) => has(fullName(d), d.vehicle, d.licence_number, d.phone_e164))
      .slice(0, 5)
      .map((d) => ({ kind: 'driver', id: d.user_id, title: fullName(d), sub: `Driver · ${d.vehicle ?? 'No car'}` }))
    const accountHits: Result[] = users
      .filter((u) => u.role !== 'driver' && has(fullName(u), u.email, u.phone_e164))
      .slice(0, 5)
      .map((u) => ({ kind: 'account', id: u.id, title: fullName(u), sub: `${u.role === 'admin' ? 'Admin' : 'Rider'} · ${u.email ?? u.phone_e164 ?? ''}` }))
    const tripHits: Result[] = rides
      .filter((r) => has(r.pickup_name, r.dropoff_name, r.rider_name, r.driver_name, r.id))
      .slice(0, 5)
      .map((r) => ({ kind: 'trip', id: r.id, title: `${r.pickup_name} → ${r.dropoff_name}`, sub: `Trip · ${r.rider_name} · ${ago(r.created_at)}` }))
    return [...pageHits, ...driverHits, ...accountHits, ...tripHits]
  }, [q, pages, drivers, users, rides])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogBackdrop className="fixed inset-0 z-50 bg-black/40" />
      <DialogContent className="fixed top-[12vh] left-1/2 z-50 w-[calc(100%-2rem)] max-w-xl -translate-x-1/2 overflow-hidden rounded-2xl border border-line bg-surface shadow-[0_24px_64px_rgb(0_0_0/0.2)]">
        <DialogTitle className="sr-only">Search Nova Ops</DialogTitle>
        <div className="flex items-center gap-3 border-b border-line px-4">
          <Search aria-hidden="true" className="size-4 text-fg-3" />
          <input
            ref={input}
            aria-label="Search people, cars and trips"
            role="combobox"
            aria-expanded="true"
            aria-controls="search-results"
            aria-activedescendant={results[index] ? `result-${index}` : undefined}
            value={q}
            onChange={(e) => {
              setQ(e.target.value)
              setIndex(0)
            }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setIndex((i) => Math.min(results.length - 1, i + 1))
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                setIndex((i) => Math.max(0, i - 1))
              } else if (e.key === 'Enter' && results[index]) {
                e.preventDefault()
                onPick(results[index])
              }
            }}
            placeholder="Search people, plates, places"
            className="h-12 flex-1 bg-transparent text-[15px] text-fg placeholder:text-fg-3 focus:outline-none"
          />
          <Kbd>esc</Kbd>
        </div>
        <ul id="search-results" role="listbox" aria-label="Results" className="max-h-[52vh] overflow-y-auto p-2">
          {results.length === 0 && <li className="px-3 py-6 text-center text-sm text-fg-3">No matches for “{q}”</li>}
          {results.map((r, i) => (
            <li key={`${r.kind}-${r.id}`} id={`result-${i}`} role="option" aria-selected={i === index}>
              <button
                type="button"
                onMouseEnter={() => setIndex(i)}
                onClick={() => onPick(r)}
                className={`flex w-full cursor-pointer items-center justify-between gap-4 rounded-[10px] px-3 py-2.5 text-left ${i === index ? 'bg-sunken' : ''}`}
              >
                <span className="min-w-0">
                  <span className="block truncate text-sm text-fg">{r.title}</span>
                  <span className="block truncate text-xs text-fg-3">{r.sub}</span>
                </span>
                {i === index && <Kbd>↵</Kbd>}
              </button>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  )
}
