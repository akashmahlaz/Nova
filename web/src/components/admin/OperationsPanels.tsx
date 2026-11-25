import { useMemo, useState } from 'react'
import { CheckCircle2, Globe, Pencil, Plus, Search, ShieldCheck, Trash2 } from 'lucide-react'
import {
  NovaBadge,
  NovaButton,
  NovaCard,
  NovaDialog,
  NovaDrawer,
  NovaSegmented,
} from './ui'
import type { AdminDriver, AdminRide, AdminServiceArea, User } from '../../lib/api'

export function DriversPanel({
  drivers,
  onStatusChange,
}: {
  drivers: AdminDriver[]
  onStatusChange: (userId: string, status: 'pending' | 'approved' | 'suspended') => Promise<void>
}) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<'all' | 'pending' | 'approved' | 'suspended'>('all')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return drivers.filter((d) => {
      if (filter !== 'all' && d.status !== filter) return false
      if (!q) return true
      return (
        `${d.first_name} ${d.last_name}`.toLowerCase().includes(q) ||
        d.licence_number.toLowerCase().includes(q) ||
        (d.vehicle?.toLowerCase().includes(q) ?? false) ||
        (d.phone_e164?.toLowerCase().includes(q) ?? false)
      )
    })
  }, [drivers, query, filter])

  return (
    <NovaCard className="p-6 space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold tracking-tight text-zinc-950 dark:text-zinc-50">
            Fleet &amp; Driver Onboarding
          </h2>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
            Approve applications or suspend drivers immediately from Redis dispatch.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-zinc-400 dark:text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Name, licence, plate…"
              className="h-9 pl-8 pr-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-500"
            />
          </div>
          <NovaSegmented
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: 'all', label: 'All' },
              { value: 'pending', label: 'Pending' },
              { value: 'approved', label: 'Approved' },
              { value: 'suspended', label: 'Suspended' },
            ]}
          />
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-zinc-200/80 dark:border-zinc-800 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
              <th className="py-3 pr-4">Driver</th>
              <th className="py-3 px-4">Licence</th>
              <th className="py-3 px-4">Active Vehicle</th>
              <th className="py-3 px-4">Status</th>
              <th className="py-3 pl-4 text-right">Control</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/80 text-xs">
            {filtered.map((d) => (
              <tr key={d.user_id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/60 transition-colors">
                <td className="py-3.5 pr-4">
                  <div className="font-medium text-zinc-950 dark:text-zinc-100">
                    {d.first_name} {d.last_name}
                  </div>
                  <div className="text-[11px] tabular-nums text-zinc-500 dark:text-zinc-400">
                    {d.phone_e164 ?? '—'}
                  </div>
                </td>
                <td className="py-3.5 px-4">
                  <div className="font-mono text-zinc-900 dark:text-zinc-100">{d.licence_number}</div>
                  <div className="text-[11px] tabular-nums text-zinc-500 dark:text-zinc-400">
                    {d.licence_expiry ? `Exp ${d.licence_expiry}` : 'No expiry set'}
                  </div>
                </td>
                <td className="py-3.5 px-4 text-zinc-600 dark:text-zinc-400">
                  {d.vehicle ?? 'No active vehicle'}
                </td>
                <td className="py-3.5 px-4">
                  <StatusPill status={d.status} />
                </td>
                <td className="py-3.5 pl-4 text-right">
                  <div className="inline-flex items-center gap-1.5">
                    {d.status !== 'approved' && (
                      <NovaButton
                        variant="emerald"
                        size="sm"
                        onClick={() => onStatusChange(d.user_id, 'approved')}
                      >
                        Approve
                      </NovaButton>
                    )}
                    {d.status !== 'suspended' && (
                      <NovaButton
                        variant="danger"
                        size="sm"
                        onClick={() => onStatusChange(d.user_id, 'suspended')}
                      >
                        Suspend
                      </NovaButton>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={5} className="py-12 text-center text-xs text-zinc-500 dark:text-zinc-400">
                  No drivers match this filter.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </NovaCard>
  )
}

export function RidesPanel({ rides }: { rides: AdminRide[] }) {
  const [filter, setFilter] = useState<'all' | 'active' | 'completed' | 'cancelled'>('all')
  const [query, setQuery] = useState('')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return rides.filter((r) => {
      const isActive = ['requested', 'accepted', 'arriving', 'in_progress'].includes(r.status)
      const isCancelled = ['cancelled', 'no_drivers'].includes(r.status)
      if (filter === 'active' && !isActive) return false
      if (filter === 'completed' && r.status !== 'completed') return false
      if (filter === 'cancelled' && !isCancelled) return false
      if (!q) return true
      return (
        r.rider_name.toLowerCase().includes(q) ||
        (r.driver_name?.toLowerCase().includes(q) ?? false) ||
        r.pickup_name.toLowerCase().includes(q) ||
        r.dropoff_name.toLowerCase().includes(q)
      )
    })
  }, [rides, filter, query])

  return (
    <NovaCard className="p-6 space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold tracking-tight text-zinc-950 dark:text-zinc-50">
            Rides &amp; Dispatch Ledger
          </h2>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
            Real-time trip telemetry from PostgreSQL across all service areas.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-zinc-400 dark:text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Rider, driver, pickup…"
              className="h-9 pl-8 pr-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-500"
            />
          </div>
          <NovaSegmented
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: 'all', label: 'All' },
              { value: 'active', label: 'Active' },
              { value: 'completed', label: 'Completed' },
              { value: 'cancelled', label: 'Cancelled' },
            ]}
          />
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-zinc-200/80 dark:border-zinc-800 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
              <th className="py-3 pr-4">Route</th>
              <th className="py-3 px-4">Rider / Driver</th>
              <th className="py-3 px-4">Product</th>
              <th className="py-3 px-4">Distance</th>
              <th className="py-3 px-4">Fare</th>
              <th className="py-3 pl-4 text-right">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/80 text-xs">
            {filtered.map((r) => (
              <tr key={r.id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/60 transition-colors">
                <td className="py-3.5 pr-4">
                  <div className="font-medium text-zinc-950 dark:text-zinc-100 truncate max-w-[260px]">
                    {r.pickup_name} → {r.dropoff_name}
                  </div>
                  <div className="text-[11px] text-zinc-500 dark:text-zinc-400">
                    {r.service_area_name} · {new Date(r.created_at).toLocaleString()}
                  </div>
                </td>
                <td className="py-3.5 px-4">
                  <div className="font-medium text-zinc-950 dark:text-zinc-100">{r.rider_name}</div>
                  <div className="text-[11px] text-zinc-500 dark:text-zinc-400">
                    Driver: {r.driver_name ?? 'Unassigned'}
                  </div>
                </td>
                <td className="py-3.5 px-4 uppercase font-mono text-[11px] text-zinc-500 dark:text-zinc-400">
                  {r.product_id}
                </td>
                <td className="py-3.5 px-4 tabular-nums text-zinc-600 dark:text-zinc-400">
                  {(r.distance_meters / 1000).toFixed(1)} km
                </td>
                <td className="py-3.5 px-4 tabular-nums font-medium text-zinc-950 dark:text-zinc-100">
                  {r.currency} {(r.fare_cents / 100).toFixed(2)}
                </td>
                <td className="py-3.5 pl-4 text-right">
                  <StatusPill status={r.status} />
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={6} className="py-12 text-center text-xs text-zinc-500 dark:text-zinc-400">
                  No rides match this filter.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </NovaCard>
  )
}

export function UsersPanel({
  users,
  currentUserId,
  onRoleChange,
  onCreateUser,
  onUpdateUser,
  onDeleteUser,
}: {
  users: User[]
  currentUserId: string
  onRoleChange: (userId: string, role: 'rider' | 'driver' | 'admin') => Promise<void>
  onCreateUser: (payload: {
    first_name: string
    last_name: string
    email?: string
    phone_e164?: string
    role: 'rider' | 'driver' | 'admin'
  }) => Promise<void>
  onUpdateUser: (
    userId: string,
    payload: {
      first_name?: string
      last_name?: string
      email?: string
      phone_e164?: string
      role?: 'rider' | 'driver' | 'admin'
      verify_email?: boolean
      verify_phone?: boolean
    },
  ) => Promise<void>
  onDeleteUser: (userId: string) => Promise<void>
}) {
  const [query, setQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState<'all' | 'rider' | 'driver' | 'admin'>('all')
  const [updatingId, setUpdatingId] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<User | null>(null)
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)

  const [cFirst, setCFirst] = useState('')
  const [cLast, setCLast] = useState('')
  const [cEmail, setCEmail] = useState('')
  const [cPhone, setCPhone] = useState('')
  const [cRole, setCRole] = useState<'rider' | 'driver' | 'admin'>('rider')
  const [formBusy, setFormBusy] = useState(false)

  const [eFirst, setEFirst] = useState('')
  const [eLast, setELast] = useState('')
  const [eEmail, setEEmail] = useState('')
  const [ePhone, setEPhone] = useState('')
  const [eRole, setERole] = useState<'rider' | 'driver' | 'admin'>('rider')

  function openEdit(u: User) {
    setEditingUser(u)
    setEFirst(u.first_name)
    setELast(u.last_name)
    setEEmail(u.email ?? '')
    setEPhone(u.phone_e164 ?? '')
    setERole(u.role)
    setConfirmDeleteId(null)
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return users.filter((u) => {
      if (roleFilter !== 'all' && u.role !== roleFilter) return false
      if (!q) return true
      return (
        `${u.first_name} ${u.last_name}`.toLowerCase().includes(q) ||
        (u.email?.toLowerCase().includes(q) ?? false) ||
        (u.phone_e164?.toLowerCase().includes(q) ?? false)
      )
    })
  }, [users, query, roleFilter])

  return (
    <NovaCard className="p-6 space-y-5">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold tracking-tight text-zinc-950 dark:text-zinc-50">
            Accounts &amp; User Management
          </h2>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
            Create, edit, verify, change roles, or remove accounts in PostgreSQL.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-zinc-400 dark:text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search name, phone, email…"
              className="h-9 pl-8 pr-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-500"
            />
          </div>
          <NovaSegmented
            value={roleFilter}
            onValueChange={setRoleFilter}
            options={[
              { value: 'all', label: 'All' },
              { value: 'rider', label: 'Rider' },
              { value: 'driver', label: 'Driver' },
              { value: 'admin', label: 'Admin' },
            ]}
          />
          <NovaButton onClick={() => setCreateOpen(true)}>
            <Plus className="w-3.5 h-3.5" />
            New User
          </NovaButton>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-zinc-200/80 dark:border-zinc-800 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
              <th className="py-3 pr-4">User</th>
              <th className="py-3 px-4">Mobile</th>
              <th className="py-3 px-4">Email</th>
              <th className="py-3 px-4">Joined</th>
              <th className="py-3 px-4">Role</th>
              <th className="py-3 pl-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/80 text-xs">
            {filtered.map((u) => {
              const isSelf = u.id === currentUserId
              return (
                <tr key={u.id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/60 transition-colors">
                  <td className="py-3.5 pr-4">
                    <div className="flex items-center gap-2.5">
                      <div className="w-8 h-8 rounded-full bg-zinc-100 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 flex items-center justify-center text-[11px] font-semibold text-zinc-800 dark:text-zinc-200 shrink-0">
                        {(u.first_name[0] ?? 'N').toUpperCase()}
                        {(u.last_name[0] ?? '').toUpperCase()}
                      </div>
                      <div>
                        <div className="font-medium text-zinc-950 dark:text-zinc-100">
                          {u.first_name} {u.last_name}{' '}
                          {isSelf && <NovaBadge tone="emerald">You</NovaBadge>}
                        </div>
                        <div className="font-mono text-[10px] text-zinc-400 dark:text-zinc-500">
                          {u.id.slice(0, 8)}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="py-3.5 px-4 tabular-nums text-zinc-600 dark:text-zinc-400">
                    <span className="inline-flex items-center gap-1.5">
                      {u.phone_e164 ?? '—'}
                      {u.phone_verified_at && (
                        <CheckCircle2 className="w-3.5 h-3.5 text-teal-600 dark:text-teal-400" />
                      )}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 text-zinc-600 dark:text-zinc-400">
                    <span className="inline-flex items-center gap-1.5">
                      {u.email ?? '—'}
                      {u.email_verified_at && (
                        <CheckCircle2 className="w-3.5 h-3.5 text-teal-600 dark:text-teal-400" />
                      )}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 tabular-nums text-zinc-500 dark:text-zinc-400">
                    {new Date(u.created_at).toLocaleDateString()}
                  </td>
                  <td className="py-3.5 px-4">
                    <select
                      value={u.role}
                      disabled={updatingId === u.id}
                      onChange={async (e) => {
                        const next = e.target.value as 'rider' | 'driver' | 'admin'
                        if (next === u.role) return
                        setUpdatingId(u.id)
                        try {
                          await onRoleChange(u.id, next)
                        } finally {
                          setUpdatingId(null)
                        }
                      }}
                      className="h-8 px-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs font-medium text-zinc-950 dark:text-zinc-100"
                    >
                      <option value="rider">rider</option>
                      <option value="driver">driver</option>
                      <option value="admin">admin</option>
                    </select>
                  </td>
                  <td className="py-3.5 pl-4 text-right">
                    <div className="inline-flex items-center gap-1">
                      <NovaButton variant="outline" size="sm" onClick={() => openEdit(u)}>
                        <Pencil className="w-3 h-3" />
                        Edit
                      </NovaButton>
                      {!isSelf && (
                        <NovaButton
                          variant="danger"
                          size="sm"
                          onClick={() => {
                            openEdit(u)
                            setConfirmDeleteId(u.id)
                          }}
                          title="Delete user"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </NovaButton>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={6} className="py-12 text-center text-xs text-zinc-500 dark:text-zinc-400">
                  No accounts match your filter.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <NovaDialog open={createOpen} onOpenChange={setCreateOpen} title="Create New Account">
        <form
          onSubmit={async (e) => {
            e.preventDefault()
            setFormBusy(true)
            try {
              await onCreateUser({
                first_name: cFirst,
                last_name: cLast,
                email: cEmail || undefined,
                phone_e164: cPhone || undefined,
                role: cRole,
              })
              setCreateOpen(false)
              setCFirst('')
              setCLast('')
              setCEmail('')
              setCPhone('')
              setCRole('rider')
            } finally {
              setFormBusy(false)
            }
          }}
          className="space-y-4"
        >
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                First name *
              </label>
              <input
                required
                value={cFirst}
                onChange={(e) => setCFirst(e.target.value)}
                className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
              />
            </div>
            <div className="space-y-1">
              <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                Last name
              </label>
              <input
                value={cLast}
                onChange={(e) => setCLast(e.target.value)}
                className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
              />
            </div>
          </div>
          <div className="space-y-1">
            <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">Email</label>
            <input
              type="email"
              value={cEmail}
              onChange={(e) => setCEmail(e.target.value)}
              placeholder="user@novataxis.co.nz"
              className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
            />
          </div>
          <div className="space-y-1">
            <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
              Mobile (E.164)
            </label>
            <input
              type="tel"
              value={cPhone}
              onChange={(e) => setCPhone(e.target.value)}
              placeholder="+6421000000"
              className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
            />
          </div>
          <div className="space-y-1">
            <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">Role</label>
            <select
              value={cRole}
              onChange={(e) => setCRole(e.target.value as 'rider' | 'driver' | 'admin')}
              className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
            >
              <option value="rider">rider</option>
              <option value="driver">driver</option>
              <option value="admin">admin</option>
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <NovaButton variant="outline" type="button" onClick={() => setCreateOpen(false)}>
              Cancel
            </NovaButton>
            <NovaButton
              type="submit"
              disabled={formBusy || !cFirst.trim() || (!cEmail.trim() && !cPhone.trim())}
            >
              {formBusy ? 'Creating…' : 'Create User'}
            </NovaButton>
          </div>
        </form>
      </NovaDialog>

      <NovaDrawer
        open={Boolean(editingUser)}
        onOpenChange={(open) => {
          if (!open) setEditingUser(null)
        }}
        eyebrow="User Inspector"
        title={editingUser ? `${editingUser.first_name} ${editingUser.last_name}` : ''}
        subtitle={editingUser?.id}
      >
        {editingUser && (
          <div className="flex flex-col justify-between gap-6">
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                    First name
                  </label>
                  <input
                    value={eFirst}
                    onChange={(e) => setEFirst(e.target.value)}
                    className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                    Last name
                  </label>
                  <input
                    value={eLast}
                    onChange={(e) => setELast(e.target.value)}
                    className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                  Email
                </label>
                <input
                  type="email"
                  value={eEmail}
                  onChange={(e) => setEEmail(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
                />
              </div>

              <div className="space-y-1">
                <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                  Mobile (E.164)
                </label>
                <input
                  type="tel"
                  value={ePhone}
                  onChange={(e) => setEPhone(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
                />
              </div>

              <div className="space-y-1">
                <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                  Role
                </label>
                <select
                  value={eRole}
                  onChange={(e) => setERole(e.target.value as 'rider' | 'driver' | 'admin')}
                  className="w-full h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs font-medium text-zinc-950 dark:text-zinc-100"
                >
                  <option value="rider">rider</option>
                  <option value="driver">driver</option>
                  <option value="admin">admin</option>
                </select>
              </div>

              <div className="rounded-xl bg-zinc-50 dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 p-3.5 space-y-2.5">
                <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  Verification Controls
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-zinc-800 dark:text-zinc-200">Email verification</span>
                  <NovaButton
                    size="sm"
                    variant={editingUser.email_verified_at ? 'emerald' : 'outline'}
                    onClick={async () => {
                      const next = !editingUser.email_verified_at
                      await onUpdateUser(editingUser.id, { verify_email: next })
                      setEditingUser((prev) =>
                        prev
                          ? { ...prev, email_verified_at: next ? new Date().toISOString() : null }
                          : null,
                      )
                    }}
                  >
                    <ShieldCheck className="w-3 h-3" />
                    {editingUser.email_verified_at ? 'Verified' : 'Mark Verified'}
                  </NovaButton>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-zinc-800 dark:text-zinc-200">Phone verification</span>
                  <NovaButton
                    size="sm"
                    variant={editingUser.phone_verified_at ? 'emerald' : 'outline'}
                    onClick={async () => {
                      const next = !editingUser.phone_verified_at
                      await onUpdateUser(editingUser.id, { verify_phone: next })
                      setEditingUser((prev) =>
                        prev
                          ? { ...prev, phone_verified_at: next ? new Date().toISOString() : null }
                          : null,
                      )
                    }}
                  >
                    <ShieldCheck className="w-3 h-3" />
                    {editingUser.phone_verified_at ? 'Verified' : 'Mark Verified'}
                  </NovaButton>
                </div>
              </div>
            </div>

            <div className="pt-5 border-t border-zinc-200 dark:border-zinc-800 space-y-3">
              <NovaButton
                className="w-full h-10"
                disabled={formBusy}
                onClick={async () => {
                  setFormBusy(true)
                  try {
                    await onUpdateUser(editingUser.id, {
                      first_name: eFirst,
                      last_name: eLast,
                      email: eEmail,
                      phone_e164: ePhone,
                      role: eRole,
                    })
                    setEditingUser(null)
                  } finally {
                    setFormBusy(false)
                  }
                }}
              >
                {formBusy ? 'Saving…' : 'Save Changes'}
              </NovaButton>

              {editingUser.id !== currentUserId &&
                (confirmDeleteId === editingUser.id ? (
                  <div className="flex items-center gap-2">
                    <NovaButton
                      className="flex-1 h-9 bg-red-600 text-white hover:bg-red-700 dark:bg-red-600 dark:text-white dark:hover:bg-red-700"
                      disabled={formBusy}
                      onClick={async () => {
                        setFormBusy(true)
                        try {
                          await onDeleteUser(editingUser.id)
                          setEditingUser(null)
                        } finally {
                          setFormBusy(false)
                        }
                      }}
                    >
                      Confirm Permanent Delete
                    </NovaButton>
                    <NovaButton variant="outline" onClick={() => setConfirmDeleteId(null)}>
                      Cancel
                    </NovaButton>
                  </div>
                ) : (
                  <NovaButton
                    variant="danger"
                    className="w-full"
                    onClick={() => setConfirmDeleteId(editingUser.id)}
                  >
                    Delete Account
                  </NovaButton>
                ))}
            </div>
          </div>
        )}
      </NovaDrawer>
    </NovaCard>
  )
}

export function ServiceAreasPanel({
  areas,
  onToggle,
}: {
  areas: AdminServiceArea[]
  onToggle: (id: string, active: boolean) => Promise<void>
}) {
  return (
    <NovaCard className="p-6 space-y-5">
      <div>
        <h2 className="text-base font-semibold tracking-tight text-zinc-950 dark:text-zinc-50">
          PostGIS Service Areas
        </h2>
        <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
          Operating geofences, currencies, and fare card coverage.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {areas.map((a) => (
          <div
            key={a.id}
            className="rounded-xl bg-zinc-50 dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 p-4 flex flex-col justify-between gap-3"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-center gap-2.5">
                <Globe className="w-4 h-4 text-teal-600 dark:text-teal-400" />
                <div>
                  <div className="text-xs font-semibold text-zinc-950 dark:text-zinc-100">
                    {a.name}
                  </div>
                  <div className="text-[11px] text-zinc-500 dark:text-zinc-400">
                    {a.region_code} · {a.currency} · {a.timezone}
                  </div>
                </div>
              </div>
              <NovaBadge tone={a.active ? 'emerald' : 'neutral'}>
                {a.active ? 'Active' : 'Paused'}
              </NovaBadge>
            </div>

            <div className="flex items-center justify-between pt-3 border-t border-zinc-200/80 dark:border-zinc-800 text-[11px] tabular-nums text-zinc-500 dark:text-zinc-400">
              <span>
                {a.rate_count} fare cards · {a.ride_count} rides
              </span>
              <NovaButton variant="outline" size="sm" onClick={() => onToggle(a.id, !a.active)}>
                {a.active ? 'Pause' : 'Activate'}
              </NovaButton>
            </div>
          </div>
        ))}
        {areas.length === 0 && (
          <div className="col-span-full py-12 text-center text-xs text-zinc-500 dark:text-zinc-400">
            No service areas configured in PostgreSQL.
          </div>
        )}
      </div>
    </NovaCard>
  )
}

export function StatusPill({ status }: { status: string }) {
  const tone =
    status === 'approved' || status === 'completed' || status === 'verified'
      ? 'emerald'
      : status === 'pending' || status === 'requested' || status === 'scheduled'
        ? 'amber'
        : status === 'suspended' ||
            status === 'cancelled' ||
            status === 'no_drivers' ||
            status === 'rejected'
          ? 'crimson'
          : 'neutral'

  return <NovaBadge tone={tone}>{status.replace('_', ' ')}</NovaBadge>
}
