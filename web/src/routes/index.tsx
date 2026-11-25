import { createFileRoute } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { Separator } from '@ui-sdk/react/separator'
import {
  Activity,
  Car,
  Check,
  Command,
  Globe,
  HardDrive,
  LayoutDashboard,
  Lock,
  LogOut,
  Menu,
  Navigation,
  PanelLeft,
  RefreshCw,
  Shield,
  TrendingUp,
  Users,
  X,
} from 'lucide-react'
import {
  type AdminDriver,
  type AdminMediaItem,
  type AdminOverview,
  type AdminRide,
  type AdminServiceArea,
  ApiError,
  type Session,
  type User,
  getStoredSession,
  novaAdminApi,
  saveStoredSession,
} from '../lib/api'
import {
  NovaBadge,
  NovaButton,
  NovaCard,
  NovaDialog,
  NovaDrawer,
  NovaSegmented,
  NovaThemeToggle,
} from '../components/admin/ui'
import { TelemetryChart } from '../components/admin/TelemetryChart'
import { S3VaultPanel } from '../components/admin/S3VaultPanel'
import {
  DriversPanel,
  RidesPanel,
  ServiceAreasPanel,
  StatusPill,
  UsersPanel,
} from '../components/admin/OperationsPanels'

export const Route = createFileRoute('/')({ component: AdminConsole })

type NavTab = 'overview' | 'users' | 'drivers' | 'rides' | 'vault' | 'areas'

function formatError(err: unknown): string {
  if (err instanceof ApiError) {
    const map: Record<string, string> = {
      account_not_found: 'No Nova account found for that email or phone.',
      invalid_code: 'That 6-digit code is incorrect.',
      code_not_found: 'Code expired or not found. Send a new one.',
      too_many_requests: 'Too many requests. Please wait 30 seconds.',
      unauthorized: 'Your account does not have admin access.',
      s3_unavailable: 'AWS S3 is not configured on the server.',
      user_already_exists: 'An account with that email or phone already exists.',
      contact_already_in_use: 'That email or phone is already used by another account.',
      cannot_delete_self: 'You cannot delete your own active admin account.',
    }
    return map[err.code] ?? `Request failed (${err.code}).`
  }
  return 'Network error. Check connection and try again.'
}

function AdminConsole() {
  const [session, setSession] = useState<Session | null>(() => getStoredSession())

  if (!session) {
    return (
      <AdminLogin
        onSuccess={(s) => {
          saveStoredSession(s)
          setSession(s)
        }}
      />
    )
  }

  if (session.user.role !== 'admin') {
    return (
      <div className="min-h-screen flex items-center justify-center p-6 bg-zinc-50 dark:bg-zinc-950 text-zinc-950 dark:text-zinc-50">
        <NovaCard className="w-full max-w-md p-7 space-y-5">
          <div className="w-10 h-10 rounded-xl bg-red-500/15 text-red-600 dark:text-red-400 flex items-center justify-center">
            <Shield className="w-5 h-5" />
          </div>
          <div className="space-y-1.5">
            <h1 className="text-lg font-bold tracking-tight">Admin access required</h1>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
              Signed in as{' '}
              <span className="font-semibold text-zinc-950 dark:text-zinc-100">
                {session.user.first_name}
              </span>{' '}
              (role:{' '}
              <code className="px-1.5 py-0.5 rounded bg-zinc-100 dark:bg-zinc-900 font-mono">
                {session.user.role}
              </code>
              ). Only accounts with{' '}
              <code className="px-1.5 py-0.5 rounded bg-zinc-100 dark:bg-zinc-900 font-mono">
                role = &apos;admin&apos;
              </code>{' '}
              in PostgreSQL can access this console.
            </p>
          </div>
          <NovaButton
            className="w-full h-10"
            onClick={async () => {
              await novaAdminApi.logout()
              setSession(null)
            }}
          >
            Sign out
          </NovaButton>
        </NovaCard>
      </div>
    )
  }

  return (
    <AdminDashboardShell
      session={session}
      onLogout={async () => {
        await novaAdminApi.logout()
        setSession(null)
      }}
    />
  )
}

function AdminLogin({ onSuccess }: { onSuccess: (s: Session) => void }) {
  const [channel, setChannel] = useState<'email' | 'phone'>('email')
  const [destination, setDestination] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submitAuth() {
    const dest = channel === 'email' ? destination.trim().toLowerCase() : destination.trim()
    if (!dest) return
    setBusy(true)
    setError(null)
    try {
      if (!codeSent) {
        await novaAdminApi.sendOtp(channel, dest)
        setCodeSent(true)
      } else {
        const session = await novaAdminApi.verifyOtp(channel, dest, code.trim())
        onSuccess(session)
      }
    } catch (err) {
      setError(formatError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-zinc-50 dark:bg-zinc-950 text-zinc-950 dark:text-zinc-50">
      <div className="absolute top-4 right-4">
        <NovaThemeToggle />
      </div>
      <NovaCard className="w-full max-w-sm p-7 space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-lg bg-zinc-950 dark:bg-zinc-100 text-white dark:text-zinc-950 flex items-center justify-center text-xs font-bold">
              N
            </div>
            <span className="text-sm font-bold tracking-tight">Nova Ops</span>
          </div>
          <NovaBadge tone="emerald">Admin</NovaBadge>
        </div>

        <div className="space-y-1">
          <h1 className="text-xl font-bold tracking-tight">Sign in</h1>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            Authenticate with a PostgreSQL <code className="font-mono">admin</code> account.
          </p>
        </div>

        <NovaSegmented
          value={channel}
          onValueChange={(c) => {
            setChannel(c)
            setCodeSent(false)
            setError(null)
          }}
          options={[
            { value: 'email', label: 'Email' },
            { value: 'phone', label: 'Phone' },
          ]}
        />

        <form
          onSubmit={(e) => {
            e.preventDefault()
            void submitAuth()
          }}
          className="space-y-3"
        >
          <div className="space-y-1">
            <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
              {channel === 'email' ? 'Admin email' : 'Mobile (+country code)'}
            </label>
            <input
              type={channel === 'email' ? 'email' : 'tel'}
              value={destination}
              disabled={codeSent}
              onChange={(e) => setDestination(e.target.value)}
              placeholder={channel === 'email' ? 'admin@novataxis.co.nz' : '+6421000000'}
              className="w-full h-10 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
            />
          </div>

          {codeSent && (
            <div className="space-y-1">
              <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                6-digit code
              </label>
              <input
                type="text"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="123456"
                className="w-full h-10 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs font-mono tracking-widest text-zinc-950 dark:text-zinc-100"
              />
            </div>
          )}

          {error && <p className="text-xs font-medium text-red-600 dark:text-red-400">{error}</p>}

          <NovaButton
            type="submit"
            className="w-full h-10"
            disabled={busy || (codeSent ? code.length !== 6 : !destination.trim())}
          >
            {busy ? 'Please wait…' : codeSent ? 'Verify & sign in' : 'Send code'}
          </NovaButton>

          {codeSent && (
            <NovaButton
              variant="ghost"
              type="button"
              className="w-full h-8"
              onClick={() => {
                setCodeSent(false)
                setCode('')
                setError(null)
              }}
            >
              Change {channel}
            </NovaButton>
          )}
        </form>
      </NovaCard>
    </div>
  )
}

function AdminDashboardShell({
  session,
  onLogout,
}: {
  session: Session
  onLogout: () => Promise<void>
}) {
  const [tab, setTab] = useState<NavTab>('overview')
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [drivers, setDrivers] = useState<AdminDriver[]>([])
  const [rides, setRides] = useState<AdminRide[]>([])
  const [areas, setAreas] = useState<AdminServiceArea[]>([])
  const [media, setMedia] = useState<AdminMediaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cmdOpen, setCmdOpen] = useState(false)

  const loadAll = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [ov, us, dr, rd, ar, md] = await Promise.all([
        novaAdminApi.getOverview(),
        novaAdminApi.listUsers(),
        novaAdminApi.listDrivers(),
        novaAdminApi.listRides(),
        novaAdminApi.listServiceAreas(),
        novaAdminApi.listMedia(),
      ])
      setOverview(ov)
      setUsers(us)
      setDrivers(dr)
      setRides(rd)
      setAreas(ar)
      setMedia(md)
    } catch (err) {
      setError(formatError(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadAll()
  }, [loadAll])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setCmdOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const navItems: { id: NavTab; label: string; icon: React.ReactNode; badge?: number }[] = [
    { id: 'overview', label: 'Command Center', icon: <LayoutDashboard className="w-4 h-4" /> },
    {
      id: 'users',
      label: 'Users & Roles',
      icon: <Users className="w-4 h-4" />,
      badge: users.length || undefined,
    },
    {
      id: 'drivers',
      label: 'Fleet & Drivers',
      icon: <Car className="w-4 h-4" />,
      badge: overview?.pending_drivers || undefined,
    },
    {
      id: 'rides',
      label: 'Rides & Dispatch',
      icon: <Navigation className="w-4 h-4" />,
      badge: overview?.active_rides || undefined,
    },
    {
      id: 'vault',
      label: 'S3 Media Vault',
      icon: <HardDrive className="w-4 h-4" />,
      badge: overview?.media_pending_review || undefined,
    },
    { id: 'areas', label: 'Service Areas', icon: <Globe className="w-4 h-4" /> },
  ]

  const pendingDriversList = drivers.filter((d) => d.status === 'pending').slice(0, 4)
  const pendingMediaList = media.filter((m) => m.status === 'uploaded').slice(0, 4)

  const sidebarBody = (
    <div className="flex h-full flex-col justify-between p-3">
      <div className="space-y-4">
        <button
          type="button"
          onClick={() => setTab('overview')}
          className="flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-left hover:bg-zinc-200/60 dark:hover:bg-zinc-800/70 cursor-pointer"
        >
          <div className="flex size-8 items-center justify-center rounded-lg bg-zinc-950 dark:bg-zinc-100 text-white dark:text-zinc-950 text-xs font-bold shrink-0">
            N
          </div>
          <div className="min-w-0">
            <div className="text-xs font-bold tracking-tight text-zinc-950 dark:text-zinc-50">
              Nova Ops
            </div>
            <div className="text-[11px] text-zinc-500 dark:text-zinc-400">Admin Console</div>
          </div>
        </button>

        <div className="space-y-1">
          <div className="px-2.5 text-[10px] font-semibold uppercase tracking-wider text-zinc-400 dark:text-zinc-500">
            Operations
          </div>
          <nav className="space-y-0.5">
            {navItems.map((item) => {
              const active = tab === item.id
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => {
                    setTab(item.id)
                    setMobileNavOpen(false)
                  }}
                  className={`flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-xs font-medium cursor-pointer transition-colors ${
                    active
                      ? 'bg-zinc-950 text-white dark:bg-zinc-100 dark:text-zinc-950'
                      : 'text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200/60 dark:hover:bg-zinc-800/70 hover:text-zinc-950 dark:hover:text-zinc-50'
                  }`}
                >
                  <span className="flex items-center gap-2.5">
                    {item.icon}
                    <span>{item.label}</span>
                  </span>
                  {item.badge !== undefined && item.badge > 0 && (
                    <span
                      className={`px-1.5 py-0.5 rounded-md text-[10px] tabular-nums font-semibold ${
                        active
                          ? 'bg-white/20 dark:bg-zinc-950/15'
                          : 'bg-zinc-200/80 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300'
                      }`}
                    >
                      {item.badge}
                    </span>
                  )}
                </button>
              )
            })}
          </nav>
        </div>
      </div>

      <div className="space-y-2.5">
        <div className="rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-zinc-950 p-3 space-y-1">
          <div className="flex items-center justify-between text-[11px] font-medium">
            <span className="flex items-center gap-1.5 text-zinc-950 dark:text-zinc-100">
              <Lock className="w-3 h-3 text-teal-600 dark:text-teal-400" />
              S3 Signed Vault
            </span>
            <span className="text-teal-700 dark:text-teal-400">
              {overview?.s3_configured ? 'Active' : 'Off'}
            </span>
          </div>
          <div className="font-mono text-[10px] text-zinc-500 dark:text-zinc-400 truncate">
            {overview?.s3_bucket ?? 'No bucket'}
          </div>
        </div>

        <div className="flex items-center justify-between px-2 py-1">
          <div className="min-w-0">
            <div className="text-xs font-medium text-zinc-950 dark:text-zinc-100 truncate">
              {session.user.first_name} {session.user.last_name}
            </div>
            <div className="text-[10px] text-zinc-500 dark:text-zinc-400 truncate">
              {session.user.email}
            </div>
          </div>
          <NovaButton variant="ghost" size="sm" onClick={onLogout} title="Sign out">
            <LogOut className="w-3.5 h-3.5" />
          </NovaButton>
        </div>
      </div>
    </div>
  )

  return (
    <div className="min-h-screen bg-zinc-100 dark:bg-zinc-900 text-zinc-950 dark:text-zinc-50 flex">
      {/* Desktop Inset Sidebar (dashboard-01 style, zero shadcn) */}
      {sidebarOpen && (
        <aside className="hidden lg:block w-64 shrink-0 sticky top-0 h-screen">
          {sidebarBody}
        </aside>
      )}

      {/* Mobile Drawer Navigation using @ui-sdk/react/drawer */}
      <NovaDrawer
        open={mobileNavOpen}
        onOpenChange={setMobileNavOpen}
        title="Navigation"
      >
        {sidebarBody}
      </NovaDrawer>

      {/* Inset Main Surface */}
      <div className="flex-1 min-w-0 lg:p-2.5 flex flex-col">
        <div className="flex-1 flex flex-col bg-white dark:bg-zinc-950 lg:rounded-2xl lg:border lg:border-zinc-200/80 dark:lg:border-zinc-800 shadow-xs overflow-hidden">
          <header className="h-12 shrink-0 border-b border-zinc-200/80 dark:border-zinc-800 px-4 lg:px-6 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <button
                type="button"
                onClick={() => setMobileNavOpen(true)}
                className="lg:hidden p-1.5 rounded-lg text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800 cursor-pointer"
                aria-label="Open menu"
              >
                <Menu className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={() => setSidebarOpen((v) => !v)}
                className="hidden lg:inline-flex p-1.5 rounded-lg text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800 cursor-pointer"
                aria-label="Toggle sidebar"
              >
                <PanelLeft className="w-4 h-4" />
              </button>
              <Separator
                orientation="vertical"
                className="h-4 w-px bg-zinc-200 dark:bg-zinc-800"
              />
              <h1 className="text-sm font-semibold text-zinc-950 dark:text-zinc-50">
                {navItems.find((n) => n.id === tab)?.label}
              </h1>
              <NovaBadge tone="neutral" className="hidden sm:inline-flex">
                <span className="w-1.5 h-1.5 rounded-full bg-teal-600 dark:bg-teal-400" />
                PostgreSQL Live
              </NovaBadge>
            </div>

            <div className="flex items-center gap-2">
              <NovaButton
                variant="outline"
                size="sm"
                onClick={() => setCmdOpen(true)}
                className="hidden md:inline-flex h-8"
              >
                <Command className="w-3 h-3" />
                <kbd className="text-[10px] font-mono">⌘K</kbd>
              </NovaButton>
              <NovaButton
                variant="outline"
                size="sm"
                disabled={loading}
                onClick={loadAll}
                className="h-8"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
                <span className="hidden sm:inline">Refresh</span>
              </NovaButton>
              <NovaThemeToggle />
            </div>
          </header>

          <main className="flex-1 p-4 md:p-6 space-y-6">
            {error && (
              <div className="rounded-xl bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 text-xs font-medium flex items-center justify-between">
                <span>{error}</span>
                <button type="button" onClick={() => setError(null)} className="p-1 cursor-pointer">
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}

            {tab === 'overview' && (
              <div className="space-y-6">
                {/* 4 Metric Cards inspired by dashboard-01 */}
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
                  <MetricCard
                    label="Gross Completed Fares"
                    value={`NZ$${((overview?.gross_fare_cents ?? 0) / 100).toFixed(2)}`}
                    badge={
                      <NovaBadge tone="emerald">
                        <TrendingUp className="w-3 h-3" />
                        {overview?.completed_rides ?? 0} trips
                      </NovaBadge>
                    }
                    footTitle={`${overview?.active_rides ?? 0} active rides right now`}
                    footSub={`${overview?.scheduled_rides ?? 0} scheduled upcoming`}
                  />
                  <MetricCard
                    label="Registered Accounts"
                    value={String(overview?.total_users ?? 0)}
                    badge={<NovaBadge>{overview?.rider_users ?? 0} riders</NovaBadge>}
                    footTitle={`${overview?.verified_phones ?? 0} verified phones`}
                    footSub={`${overview?.verified_emails ?? 0} verified emails`}
                  />
                  <MetricCard
                    label="Fleet Readiness"
                    value={`${overview?.approved_drivers ?? 0}/${overview?.total_drivers ?? 0}`}
                    badge={<NovaBadge tone="amber">{overview?.pending_drivers ?? 0} pending</NovaBadge>}
                    footTitle="Approved active drivers"
                    footSub={`${overview?.suspended_drivers ?? 0} suspended accounts`}
                  />
                  <MetricCard
                    label="S3 Media Vault"
                    value={String(overview?.media_total ?? 0)}
                    badge={
                      <NovaBadge tone="amber">
                        {overview?.media_pending_review ?? 0} review
                      </NovaBadge>
                    }
                    footTitle={`${overview?.media_verified ?? 0} verified documents`}
                    footSub={`${overview?.active_service_areas ?? 0}/${overview?.total_service_areas ?? 0} active zones`}
                  />
                </div>

                {/* Telemetry + Action Queue */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                  <div className="lg:col-span-2">
                    <TelemetryChart data={overview?.daily_rides ?? []} />
                  </div>

                  <NovaCard className="p-5 flex flex-col justify-between gap-4">
                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                          Action Queue
                        </span>
                        <span className="text-[11px] tabular-nums text-zinc-500 dark:text-zinc-400">
                          {(overview?.pending_drivers ?? 0) + (overview?.media_pending_review ?? 0)}{' '}
                          open
                        </span>
                      </div>

                      {pendingDriversList.map((d) => (
                        <div
                          key={d.user_id}
                          className="rounded-xl bg-zinc-50 dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 p-3 flex items-center justify-between gap-2"
                        >
                          <div className="min-w-0">
                            <div className="text-xs font-medium text-zinc-950 dark:text-zinc-100 truncate">
                              {d.first_name} {d.last_name}
                            </div>
                            <div className="text-[11px] text-zinc-500 dark:text-zinc-400">
                              Driver · Licence {d.licence_number}
                            </div>
                          </div>
                          <NovaButton
                            variant="emerald"
                            size="sm"
                            onClick={async () => {
                              await novaAdminApi.updateDriverStatus(d.user_id, 'approved')
                              await loadAll()
                            }}
                          >
                            Approve
                          </NovaButton>
                        </div>
                      ))}

                      {pendingMediaList.map((m) => (
                        <div
                          key={m.id}
                          className="rounded-xl bg-zinc-50 dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 p-3 flex items-center justify-between gap-2"
                        >
                          <div className="min-w-0">
                            <div className="text-xs font-medium text-zinc-950 dark:text-zinc-100 truncate">
                              {m.owner_name}
                            </div>
                            <div className="text-[11px] text-zinc-500 dark:text-zinc-400 truncate">
                              S3 {m.category.replace('_', ' ')}
                            </div>
                          </div>
                          <NovaButton
                            size="sm"
                            onClick={async () => {
                              await novaAdminApi.reviewMedia(m.id, 'verified')
                              await loadAll()
                            }}
                          >
                            <Check className="w-3 h-3" />
                            Verify
                          </NovaButton>
                        </div>
                      ))}

                      {pendingDriversList.length === 0 && pendingMediaList.length === 0 && (
                        <div className="py-8 text-center text-xs text-zinc-500 dark:text-zinc-400">
                          All driver applications and S3 documents are reviewed.
                        </div>
                      )}
                    </div>

                    <div className="pt-3 border-t border-zinc-200/80 dark:border-zinc-800 flex items-center justify-between text-[11px] text-zinc-500 dark:text-zinc-400">
                      <span className="inline-flex items-center gap-1.5">
                        <Lock className="w-3 h-3 text-teal-600 dark:text-teal-400" />
                        {overview?.s3_bucket ?? 'S3 Vault'}
                      </span>
                      <button
                        type="button"
                        onClick={() => setTab('vault')}
                        className="font-medium text-teal-700 dark:text-teal-400 hover:underline cursor-pointer"
                      >
                        Open Vault →
                      </button>
                    </div>
                  </NovaCard>
                </div>

                {/* Latest Dispatch Activity */}
                <NovaCard className="p-6 space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Activity className="w-4 h-4 text-teal-600 dark:text-teal-400" />
                      <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                        Latest Dispatch Activity
                      </h2>
                    </div>
                    <button
                      type="button"
                      onClick={() => setTab('rides')}
                      className="text-xs font-medium text-teal-700 dark:text-teal-400 hover:underline cursor-pointer"
                    >
                      View all rides →
                    </button>
                  </div>
                  <div className="divide-y divide-zinc-100 dark:divide-zinc-800/80">
                    {rides.slice(0, 5).map((r) => (
                      <div key={r.id} className="py-3 flex items-center justify-between gap-4 text-xs">
                        <div className="min-w-0">
                          <div className="font-medium text-zinc-950 dark:text-zinc-100 truncate">
                            {r.pickup_name} → {r.dropoff_name}
                          </div>
                          <div className="text-[11px] text-zinc-500 dark:text-zinc-400">
                            {r.rider_name} · {r.service_area_name}
                          </div>
                        </div>
                        <div className="flex items-center gap-3 shrink-0">
                          <span className="tabular-nums font-medium text-zinc-950 dark:text-zinc-100">
                            {r.currency} {(r.fare_cents / 100).toFixed(2)}
                          </span>
                          <StatusPill status={r.status} />
                        </div>
                      </div>
                    ))}
                    {rides.length === 0 && (
                      <div className="py-6 text-center text-xs text-zinc-500 dark:text-zinc-400">
                        No rides recorded yet.
                      </div>
                    )}
                  </div>
                </NovaCard>
              </div>
            )}

            {tab === 'users' && (
              <UsersPanel
                users={users}
                currentUserId={session.user.id}
                onRoleChange={async (userId, role) => {
                  const updated = await novaAdminApi.updateRole(userId, role)
                  setUsers((prev) => prev.map((u) => (u.id === userId ? updated : u)))
                }}
                onCreateUser={async (payload) => {
                  try {
                    await novaAdminApi.createUser(payload)
                    await loadAll()
                  } catch (err) {
                    setError(formatError(err))
                    throw err
                  }
                }}
                onUpdateUser={async (userId, payload) => {
                  try {
                    await novaAdminApi.updateUser(userId, payload)
                    await loadAll()
                  } catch (err) {
                    setError(formatError(err))
                    throw err
                  }
                }}
                onDeleteUser={async (userId) => {
                  try {
                    await novaAdminApi.deleteUser(userId)
                    await loadAll()
                  } catch (err) {
                    setError(formatError(err))
                    throw err
                  }
                }}
              />
            )}

            {tab === 'drivers' && (
              <DriversPanel
                drivers={drivers}
                onStatusChange={async (userId, status) => {
                  await novaAdminApi.updateDriverStatus(userId, status)
                  await loadAll()
                }}
              />
            )}

            {tab === 'rides' && <RidesPanel rides={rides} />}

            {tab === 'vault' && (
              <S3VaultPanel
                media={media}
                overview={overview}
                onUpload={async (file, category) => {
                  await novaAdminApi.uploadToS3(file, category)
                  await loadAll()
                }}
                onReview={async (id, status, reason) => {
                  await novaAdminApi.reviewMedia(id, status, reason)
                  await loadAll()
                }}
                onDelete={async (id) => {
                  await novaAdminApi.deleteMedia(id)
                  await loadAll()
                }}
              />
            )}

            {tab === 'areas' && (
              <ServiceAreasPanel
                areas={areas}
                onToggle={async (id, active) => {
                  await novaAdminApi.toggleServiceArea(id, active)
                  await loadAll()
                }}
              />
            )}
          </main>
        </div>
      </div>

      <NovaDialog open={cmdOpen} onOpenChange={setCmdOpen} title="Quick Jump">
        <div className="space-y-1">
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                setTab(item.id)
                setCmdOpen(false)
              }}
              className="w-full px-3 py-2.5 rounded-xl text-xs font-medium text-zinc-950 dark:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-zinc-900 flex items-center justify-between cursor-pointer"
            >
              <span className="flex items-center gap-2.5">
                {item.icon}
                {item.label}
              </span>
              {item.badge !== undefined && item.badge > 0 && (
                <span className="text-[11px] tabular-nums text-zinc-500 dark:text-zinc-400 font-semibold">
                  {item.badge}
                </span>
              )}
            </button>
          ))}
        </div>
      </NovaDialog>
    </div>
  )
}

function MetricCard({
  label,
  value,
  badge,
  footTitle,
  footSub,
}: {
  label: string
  value: string
  badge: React.ReactNode
  footTitle: string
  footSub: string
}) {
  return (
    <NovaCard gradient className="p-5 flex flex-col justify-between gap-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-xs text-zinc-500 dark:text-zinc-400">{label}</div>
          <div className="text-2xl font-semibold tracking-tight tabular-nums text-zinc-950 dark:text-zinc-50 mt-1">
            {value}
          </div>
        </div>
        {badge}
      </div>
      <div className="space-y-0.5 text-xs">
        <div className="font-medium text-zinc-950 dark:text-zinc-100">{footTitle}</div>
        <div className="text-zinc-500 dark:text-zinc-400">{footSub}</div>
      </div>
    </NovaCard>
  )
}
