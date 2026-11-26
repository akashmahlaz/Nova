import { createFileRoute } from '@tanstack/react-router'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Activity,
  Car,
  ChevronRight,
  ChevronsUpDown,
  CircleUser,
  FolderLock,
  LayoutDashboard,
  LogOut,
  MapPinned,
  Menu as MenuIcon,
  Navigation,
  PanelLeft,
  RefreshCw,
  Search,
  Users,
} from 'lucide-react'
import { Menu, MenuContent, MenuGroup, MenuGroupLabel, MenuItem, MenuSeparator, MenuTrigger } from '@ui-sdk/react/menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@ui-sdk/react/tooltip'
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
import { Avatar, Button, Field, IconButton, Kbd, MENU, MENU_ITEM, MENU_LABEL, MENU_SEPARATOR, PageHeader, Segmented, SidePanel, TIP, TextInput, ThemeSwitch, Toasts } from '../components/admin/ui'
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
  // Desktop sidebar collapses to icons, like sidebar-07. Remembered across visits; ⌘B toggles.
  const [collapsed, setCollapsed] = useState(false)
  const toggleSidebar = useCallback(() => {
    setCollapsed((c) => {
      window.localStorage.setItem('nova_sidebar_collapsed', c ? '0' : '1')
      return !c
    })
  }, [])
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
        // 180 days covers the chart's longest range (90 days) and the 90 before it.
        novaAdminApi.getOverview(180),
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
    setCollapsed(window.localStorage.getItem('nova_sidebar_collapsed') === '1')
    void load()
    // Live numbers stay fresh while the console is open and visible.
    const poll = window.setInterval(() => document.visibilityState === 'visible' && void load(), 30_000)
    const clock = window.setInterval(() => tick((t) => t + 1), 15_000)
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setSearchOpen((v) => !v)
      }
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'b') {
        e.preventDefault()
        toggleSidebar()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => {
      window.clearInterval(poll)
      window.clearInterval(clock)
      window.removeEventListener('keydown', onKey)
    }
  }, [load, toggleSidebar])

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

  const openAreas = areas.filter((a) => a.active).length
  const contact = session.user.email ?? session.user.phone_e164 ?? ''

  /** sidebar-07 shape: switcher on top, grouped nav, user menu at the bottom. `mini` is the icon rail. */
  const sidebar = (mini: boolean) => (
    <div className={`flex h-full flex-col pt-3 pb-3 ${mini ? 'items-center px-2' : 'px-3'}`}>
      <Menu>
        <MenuTrigger
          aria-label="Nova Ops, service areas"
          className={`flex cursor-pointer items-center gap-2.5 rounded-[10px] text-left outline-none transition-colors hover:bg-surface/60 data-open:bg-surface data-open:ring-1 data-open:ring-line ${mini ? 'size-10 justify-center' : 'h-12 w-full px-2'}`}
        >
          <span aria-hidden="true" className="inline-flex size-8 shrink-0 items-center justify-center rounded-lg bg-volt text-[13px] font-semibold text-on-volt">
            N
          </span>
          {!mini && (
            <>
              <span className="min-w-0 flex-1 leading-tight">
                <span className="block truncate text-sm font-medium text-fg">Nova Ops</span>
                <span className="block truncate text-xs text-fg-3">
                  {count(openAreas)} of {count(areas.length)} areas open
                </span>
              </span>
              <ChevronsUpDown aria-hidden="true" className="size-4 text-fg-3" />
            </>
          )}
        </MenuTrigger>
        <MenuContent side={mini ? 'right' : 'bottom'} align="start" sideOffset={6} className={MENU}>
          <MenuGroup>
            <MenuGroupLabel className={MENU_LABEL}>Service areas</MenuGroupLabel>
            {areas.map((a) => (
              <MenuItem key={a.id} value={a.id} className={MENU_ITEM} onSelect={() => go('areas')}>
                <MapPinned aria-hidden="true" />
                <span className="flex-1 truncate">{a.name}</span>
                <span className={`text-xs ${a.active ? 'text-good' : 'text-fg-3'}`}>{a.active ? 'Open' : 'Paused'}</span>
              </MenuItem>
            ))}
            {areas.length === 0 && <p className="px-2 py-1.5 text-sm text-fg-3">No service areas yet</p>}
          </MenuGroup>
          <div role="separator" className={MENU_SEPARATOR} />
          <MenuItem value="manage-areas" className={MENU_ITEM} onSelect={() => go('areas')}>
            <ChevronRight aria-hidden="true" />
            Manage areas and prices
          </MenuItem>
        </MenuContent>
      </Menu>

      <nav aria-label="Console" className={`mt-5 flex-1 space-y-5 ${mini ? 'w-full' : ''}`}>
        {groups.map((g) => (
          <div key={g.label}>
            {mini ? <div aria-hidden="true" className="mx-auto mb-2 h-px w-6 bg-line first:hidden" /> : <p className="px-2 pb-2 text-xs text-fg-3">{g.label}</p>}
            <ul className={`space-y-0.5 ${mini ? 'flex flex-col items-center' : ''}`}>
              {g.items.map((item) => {
                const active = page === item.id
                const look = `flex h-9 cursor-pointer items-center gap-2.5 rounded-[10px] text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-fg ${active ? 'bg-surface font-medium text-fg shadow-[0_1px_2px_rgb(0_0_0/0.05)] ring-1 ring-line' : 'text-fg-2 hover:bg-surface/60 hover:text-fg'}`
                const badge = item.count !== undefined && item.count > 0 ? item.count : null
                return (
                  <li key={item.id} className={mini ? '' : 'w-full'}>
                    {mini ? (
                      <Tooltip>
                        <TooltipTrigger aria-label={item.label} onClick={() => go(item.id)} aria-current={active ? 'page' : undefined} className={`${look} relative size-9 justify-center`}>
                          <span className={active ? 'text-fg' : 'text-fg-3'}>{item.icon}</span>
                          {badge !== null && <span aria-hidden="true" className="absolute top-1.5 right-1.5 size-1.5 rounded-full bg-volt ring-2 ring-canvas" />}
                        </TooltipTrigger>
                        <TooltipContent side="right" sideOffset={8} className={TIP}>
                          {item.label}
                          {badge !== null && ` · ${count(badge)}`}
                        </TooltipContent>
                      </Tooltip>
                    ) : (
                      <button type="button" onClick={() => go(item.id)} aria-current={active ? 'page' : undefined} className={`${look} w-full px-2`}>
                        <span className={active ? 'text-fg' : 'text-fg-3'}>{item.icon}</span>
                        <span className="flex-1 text-left">{item.label}</span>
                        {badge !== null && <span className="font-mono text-xs text-fg-3">{count(badge)}</span>}
                      </button>
                    )}
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </nav>

      <Menu>
        <MenuTrigger
          aria-label={`${fullName(session.user)}, account menu`}
          className={`flex cursor-pointer items-center gap-2.5 rounded-[10px] text-left outline-none transition-colors hover:bg-surface/60 data-open:bg-surface data-open:ring-1 data-open:ring-line ${mini ? 'size-10 justify-center' : 'h-12 w-full px-2'}`}
        >
          <Avatar text={initials(session.user)} size="md" />
          {!mini && (
            <>
              <span className="min-w-0 flex-1 leading-tight">
                <span className="block truncate text-sm font-medium text-fg">{fullName(session.user)}</span>
                <span className="block truncate text-xs text-fg-3">{contact}</span>
              </span>
              <ChevronsUpDown aria-hidden="true" className="size-4 text-fg-3" />
            </>
          )}
        </MenuTrigger>
        <MenuContent side={mini ? 'right' : 'top'} align="end" sideOffset={6} className={MENU}>
          <div className="flex items-center gap-2.5 px-2 py-2">
            <Avatar text={initials(session.user)} size="md" />
            <span className="min-w-0 leading-tight">
              <span className="block truncate text-sm font-medium text-fg">{fullName(session.user)}</span>
              <span className="block truncate text-xs text-fg-3">{contact}</span>
            </span>
          </div>
          <div role="separator" className={MENU_SEPARATOR} />
          <MenuItem
            value="account"
            className={MENU_ITEM}
            onSelect={() => {
              go('accounts')
              setOpenAccount(session.user.id)
            }}
          >
            <CircleUser aria-hidden="true" />
            Your account
          </MenuItem>
          <MenuSeparator className={MENU_SEPARATOR} />
          <MenuItem value="sign-out" className={MENU_ITEM} onSelect={() => onLogout()}>
            <LogOut aria-hidden="true" />
            Sign out
          </MenuItem>
        </MenuContent>
      </Menu>
    </div>
  )

  const currentGroup = groups.find((g) => g.items.some((i) => i.id === page))
  const currentLabel = currentGroup?.items.find((i) => i.id === page)?.label ?? ''

  return (
    <div className="flex min-h-screen bg-canvas text-fg">
      <aside
        aria-label="Sidebar"
        data-collapsed={collapsed ? '' : undefined}
        className={`sticky top-0 hidden h-screen shrink-0 transition-[width] duration-200 ease-out lg:block ${collapsed ? 'w-14' : 'w-[248px]'}`}
      >
        {sidebar(collapsed)}
      </aside>
      <SidePanel open={navOpen} onOpenChange={setNavOpen} title="Menu" width="max-w-[280px]">
        <div className="-mx-6 -my-5 h-full">{sidebar(false)}</div>
      </SidePanel>

      <div className="flex min-w-0 flex-1 flex-col lg:py-2 lg:pr-2">
        <div className="flex flex-1 flex-col bg-surface lg:rounded-2xl lg:shadow-[0_1px_2px_rgb(0_0_0/0.04)] lg:ring-1 lg:ring-line">
          <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-line bg-surface/95 px-4 backdrop-blur-sm lg:rounded-t-2xl lg:px-6">
            <IconButton label="Open menu" className="-ml-1 lg:hidden" onClick={() => setNavOpen(true)}>
              <MenuIcon className="size-4" />
            </IconButton>
            <Tooltip>
              <TooltipTrigger
                aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
                onClick={toggleSidebar}
                aria-expanded={!collapsed}
                className="-ml-2 hidden size-9 cursor-pointer items-center justify-center rounded-[10px] text-fg-2 transition-colors hover:bg-sunken hover:text-fg lg:inline-flex"
              >
                <PanelLeft className="size-4" />
              </TooltipTrigger>
              <TooltipContent side="bottom" sideOffset={6} className={TIP}>
                {collapsed ? 'Expand sidebar' : 'Collapse sidebar'} · ⌘B
              </TooltipContent>
            </Tooltip>
            <span aria-hidden="true" className="mx-1 h-4 w-px bg-line-strong" />
            <nav aria-label="Breadcrumb" className="min-w-0">
              <ol className="flex items-center gap-1.5 text-sm">
                {currentGroup && (
                  <li className="hidden items-center gap-1.5 text-fg-3 md:flex">
                    {currentGroup.label}
                    <ChevronRight aria-hidden="true" className="size-3.5" />
                  </li>
                )}
                <li aria-current="page" className="truncate font-medium text-fg">
                  {currentLabel}
                </li>
              </ol>
            </nav>
            <button
              type="button"
              onClick={() => setSearchOpen(true)}
              className="ml-auto hidden h-9 w-72 cursor-pointer items-center gap-2 rounded-[10px] border border-line-strong bg-surface px-3 text-sm text-fg-3 transition-colors hover:border-fg-3 md:flex"
            >
              <Search className="size-4" />
              <span className="flex-1 text-left">Search people, cars, trips</span>
              <Kbd>⌘K</Kbd>
            </button>
            <div className="ml-auto flex items-center gap-2 md:ml-0">
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
