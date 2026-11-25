import { createFileRoute } from '@tanstack/react-router'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  CheckCircle2,
  LogOut,
  Mail,
  MapPin,
  RefreshCw,
  Search,
  Shield,
  Smartphone,
  Users,
} from 'lucide-react'
import {
  type AdminStats,
  ApiError,
  type Session,
  type User,
  getStoredSession,
  novaAdminApi,
  saveStoredSession,
} from '../lib/api'

export const Route = createFileRoute('/')({ component: AdminConsole })

function formatError(err: unknown): string {
  if (err instanceof ApiError) {
    const map: Record<string, string> = {
      account_not_found: 'No Nova account found for that email or phone.',
      invalid_code: 'That 6-digit code is incorrect.',
      code_not_found: 'Code expired or not found. Send a new one.',
      too_many_requests: 'Too many requests. Please wait 30 seconds.',
      unauthorized: 'Your account does not have admin access.',
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
      <div className="min-h-screen flex items-center justify-center p-6 bg-[#FBF8FF]">
        <div className="w-full max-w-md rounded-3xl bg-[#F5F2FC] border border-[#191A2C]/10 p-8 space-y-5">
          <div className="w-12 h-12 rounded-2xl bg-[#FFDBCF] text-[#3A0B00] flex items-center justify-center">
            <Shield className="w-6 h-6" />
          </div>
          <div className="space-y-2">
            <h1 className="text-2xl font-extrabold tracking-tight text-[#191A2C]">
              Admin access required
            </h1>
            <p className="text-sm text-[#4A4B5E] leading-relaxed">
              Signed in as <span className="font-semibold text-[#191A2C]">{session.user.first_name}</span>{' '}
              (role: <code className="px-1.5 py-0.5 rounded bg-[#E9E7F2] font-mono text-xs">{session.user.role}</code>).
              Only users with <code className="px-1.5 py-0.5 rounded bg-[#E9E7F2] font-mono text-xs">role = &apos;admin&apos;</code> in PostgreSQL can view this console.
            </p>
          </div>
          <button
            type="button"
            onClick={async () => {
              await novaAdminApi.logout()
              setSession(null)
            }}
            className="w-full h-12 rounded-full bg-[#3549D6] text-white font-bold text-sm hover:bg-[#2B3DB8] transition-colors cursor-pointer"
          >
            Sign out
          </button>
        </div>
      </div>
    )
  }

  return (
    <AdminDashboard
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

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const dest = channel === 'email' ? destination.trim().toLowerCase() : destination.trim()
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
    <div className="min-h-screen flex items-center justify-center p-6 bg-[#FBF8FF]">
      <div className="w-full max-w-md rounded-3xl bg-[#F5F2FC] border border-[#191A2C]/10 p-8 space-y-6">
        <div className="flex items-center justify-between">
          <span className="text-2xl font-extrabold tracking-tight text-[#3549D6]">nova</span>
          <span className="px-3 py-1 rounded-full bg-[#DFE0FF] text-[#0A1A7A] text-xs font-bold">
            Admin Console
          </span>
        </div>

        <div className="space-y-1.5">
          <h1 className="text-3xl font-extrabold tracking-tight text-[#191A2C]">
            Sign in
          </h1>
          <p className="text-sm text-[#4A4B5E]">
            Use an account with <code className="font-mono text-xs">admin</code> role in PostgreSQL.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-1.5 p-1 rounded-full bg-[#E9E7F2]">
          <button
            type="button"
            onClick={() => {
              setChannel('email')
              setCodeSent(false)
              setError(null)
            }}
            className={`h-10 rounded-full text-sm font-bold flex items-center justify-center gap-2 transition-colors cursor-pointer ${
              channel === 'email'
                ? 'bg-[#3549D6] text-white'
                : 'text-[#4A4B5E] hover:text-[#191A2C]'
            }`}
          >
            <Mail className="w-4 h-4" />
            Email
          </button>
          <button
            type="button"
            onClick={() => {
              setChannel('phone')
              setCodeSent(false)
              setError(null)
            }}
            className={`h-10 rounded-full text-sm font-bold flex items-center justify-center gap-2 transition-colors cursor-pointer ${
              channel === 'phone'
                ? 'bg-[#3549D6] text-white'
                : 'text-[#4A4B5E] hover:text-[#191A2C]'
            }`}
          >
            <Smartphone className="w-4 h-4" />
            Phone
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-[#4A4B5E]">
              {channel === 'email' ? 'Admin email' : 'Mobile (+country code)'}
            </label>
            <input
              type={channel === 'email' ? 'email' : 'tel'}
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
              disabled={codeSent}
              placeholder={channel === 'email' ? 'admin@novaride.co' : '+917814002784'}
              className="w-full h-12 px-4 rounded-2xl bg-white border border-[#191A2C]/15 text-sm font-semibold text-[#191A2C] focus:outline-none focus:border-[#3549D6] disabled:opacity-60"
              required
            />
          </div>

          {codeSent && (
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-[#4A4B5E]">
                6-digit verification code
              </label>
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="123456"
                className="w-full h-12 px-4 rounded-2xl bg-white border border-[#191A2C]/15 text-base font-bold tracking-[0.25em] tabular-nums text-[#191A2C] focus:outline-none focus:border-[#3549D6]"
                autoFocus
                required
              />
            </div>
          )}

          {error && (
            <p className="text-sm font-semibold text-[#BA1A1A]">{error}</p>
          )}

          <button
            type="submit"
            disabled={busy || (codeSent ? code.length !== 6 : !destination.trim())}
            className="w-full h-12 rounded-full bg-[#3549D6] text-white font-bold text-sm hover:bg-[#2B3DB8] disabled:opacity-50 transition-colors cursor-pointer"
          >
            {busy ? 'Please wait…' : codeSent ? 'Verify & sign in' : 'Send code'}
          </button>

          {codeSent && (
            <button
              type="button"
              onClick={() => {
                setCodeSent(false)
                setCode('')
                setError(null)
              }}
              className="w-full text-xs font-bold text-[#3549D6] hover:underline cursor-pointer"
            >
              Change {channel}
            </button>
          )}
        </form>
      </div>
    </div>
  )
}

function AdminDashboard({
  session,
  onLogout,
}: {
  session: Session
  onLogout: () => Promise<void>
}) {
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState<'all' | 'rider' | 'driver' | 'admin'>('all')
  const [updatingId, setUpdatingId] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [s, u] = await Promise.all([novaAdminApi.getStats(), novaAdminApi.listUsers()])
      setStats(s)
      setUsers(u)
    } catch (err) {
      setError(formatError(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const filteredUsers = useMemo(() => {
    const q = query.trim().toLowerCase()
    return users.filter((u) => {
      if (roleFilter !== 'all' && u.role !== roleFilter) return false
      if (!q) return true
      return (
        u.first_name.toLowerCase().includes(q) ||
        u.last_name.toLowerCase().includes(q) ||
        (u.email?.toLowerCase().includes(q) ?? false) ||
        (u.phone_e164?.toLowerCase().includes(q) ?? false)
      )
    })
  }, [users, query, roleFilter])

  async function handleRoleChange(userId: string, role: 'rider' | 'driver' | 'admin') {
    setUpdatingId(userId)
    setError(null)
    try {
      const updated = await novaAdminApi.updateRole(userId, role)
      setUsers((prev) => prev.map((u) => (u.id === userId ? updated : u)))
    } catch (err) {
      setError(formatError(err))
    } finally {
      setUpdatingId(null)
    }
  }

  return (
    <div className="min-h-screen bg-[#FBF8FF]">
      <header className="sticky top-0 z-10 bg-[#FBF8FF]/90 backdrop-blur border-b border-[#191A2C]/10">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="text-2xl font-extrabold tracking-tight text-[#3549D6]">nova</span>
            <span className="px-2.5 py-0.5 rounded-full bg-[#DFE0FF] text-[#0A1A7A] text-xs font-bold">
              Admin
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={loadData}
              disabled={loading}
              className="h-9 px-3.5 rounded-full bg-[#EFEDF7] text-[#191A2C] text-xs font-bold flex items-center gap-1.5 hover:bg-[#E9E7F2] cursor-pointer"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              Refresh
            </button>
            <span className="hidden sm:inline text-xs font-semibold text-[#4A4B5E] px-2">
              {session.user.first_name}
            </span>
            <button
              type="button"
              onClick={onLogout}
              className="h-9 px-3.5 rounded-full bg-[#FFDBCF] text-[#3A0B00] text-xs font-bold flex items-center gap-1.5 hover:opacity-90 cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5" />
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8 space-y-8">
        {error && (
          <div className="rounded-2xl bg-[#FFDBCF] text-[#3A0B00] px-4 py-3 text-sm font-semibold">
            {error}
          </div>
        )}

        <section className="grid grid-cols-2 lg:grid-cols-5 gap-4">
          <MetricCard
            label="Total users"
            value={stats?.total_users ?? 0}
            icon={<Users className="w-5 h-5" />}
            tone="bg-[#DFE0FF] text-[#0A1A7A]"
          />
          <MetricCard
            label="Verified phones"
            value={stats?.verified_phones ?? 0}
            icon={<Smartphone className="w-5 h-5" />}
            tone="bg-[#B7F1CC] text-[#00210E]"
          />
          <MetricCard
            label="Verified emails"
            value={stats?.verified_emails ?? 0}
            icon={<Mail className="w-5 h-5" />}
            tone="bg-[#B7F1CC] text-[#00210E]"
          />
          <MetricCard
            label="Saved places"
            value={stats?.saved_places ?? 0}
            icon={<MapPin className="w-5 h-5" />}
            tone="bg-[#E0E0F9] text-[#191A2C]"
          />
          <MetricCard
            label="Trusted contacts"
            value={stats?.emergency_contacts ?? 0}
            icon={<Shield className="w-5 h-5" />}
            tone="bg-[#FFDBCF] text-[#3A0B00]"
          />
        </section>

        <section className="rounded-3xl bg-[#F5F2FC] border border-[#191A2C]/10 p-6 space-y-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-xl font-extrabold tracking-tight text-[#191A2C]">
                Accounts &amp; roles
              </h2>
              <p className="text-xs text-[#4A4B5E]">
                Live PostgreSQL directory — update user roles in real time.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div className="relative">
                <Search className="w-4 h-4 text-[#4A4B5E] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search name, phone, email"
                  className="h-10 pl-9 pr-4 rounded-full bg-white border border-[#191A2C]/10 text-xs font-semibold text-[#191A2C] focus:outline-none focus:border-[#3549D6]"
                />
              </div>

              <div className="flex items-center gap-1 p-1 rounded-full bg-[#E9E7F2]">
                {(['all', 'rider', 'driver', 'admin'] as const).map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => setRoleFilter(r)}
                    className={`h-8 px-3 rounded-full text-xs font-bold capitalize transition-colors cursor-pointer ${
                      roleFilter === r
                        ? 'bg-[#3549D6] text-white'
                        : 'text-[#4A4B5E] hover:text-[#191A2C]'
                    }`}
                  >
                    {r}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[#191A2C]/10 text-[11px] font-bold uppercase tracking-wider text-[#4A4B5E]">
                  <th className="py-3 pr-4">User</th>
                  <th className="py-3 px-4">Mobile</th>
                  <th className="py-3 px-4">Email</th>
                  <th className="py-3 px-4">Joined</th>
                  <th className="py-3 pl-4 text-right">Role</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#191A2C]/8 text-sm">
                {filteredUsers.map((u) => (
                  <tr key={u.id} className="hover:bg-[#EFEDF7]/60">
                    <td className="py-3.5 pr-4 font-bold text-[#191A2C]">
                      {u.first_name} {u.last_name}
                    </td>
                    <td className="py-3.5 px-4 tabular-nums text-xs text-[#4A4B5E]">
                      <div className="flex items-center gap-1.5">
                        <span>{u.phone_e164 ?? '—'}</span>
                        {u.phone_verified_at && (
                          <CheckCircle2 className="w-3.5 h-3.5 text-[#1B6C3A]" />
                        )}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#4A4B5E]">
                      <div className="flex items-center gap-1.5">
                        <span>{u.email ?? '—'}</span>
                        {u.email_verified_at && (
                          <CheckCircle2 className="w-3.5 h-3.5 text-[#1B6C3A]" />
                        )}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 tabular-nums text-xs text-[#4A4B5E]">
                      {new Date(u.created_at).toLocaleDateString()}
                    </td>
                    <td className="py-3.5 pl-4 text-right">
                      <select
                        value={u.role}
                        disabled={updatingId === u.id}
                        onChange={(e) =>
                          handleRoleChange(u.id, e.target.value as 'rider' | 'driver' | 'admin')
                        }
                        className="h-8 px-3 rounded-full bg-white border border-[#191A2C]/15 text-xs font-bold text-[#191A2C] focus:outline-none focus:border-[#3549D6] cursor-pointer"
                      >
                        <option value="rider">rider</option>
                        <option value="driver">driver</option>
                        <option value="admin">admin</option>
                      </select>
                    </td>
                  </tr>
                ))}
                {!loading && filteredUsers.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-sm text-[#4A4B5E]">
                      No accounts match your filter.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  )
}

function MetricCard({
  label,
  value,
  icon,
  tone,
}: {
  label: string
  value: number
  icon: React.ReactNode
  tone: string
}) {
  return (
    <div className="rounded-3xl bg-[#F5F2FC] border border-[#191A2C]/10 p-5 space-y-3">
      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center ${tone}`}>
        {icon}
      </div>
      <div>
        <div className="text-3xl font-extrabold tracking-tight tabular-nums text-[#191A2C]">
          {value}
        </div>
        <div className="text-xs font-semibold text-[#4A4B5E] mt-0.5">{label}</div>
      </div>
    </div>
  )
}
