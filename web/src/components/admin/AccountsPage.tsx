import { useEffect, useMemo, useState } from 'react'
import { Plus } from 'lucide-react'
import { type AdminUser, type AuditEntry, novaAdminApi } from '../../lib/api'
import { formatError } from '../../lib/errors'
import { ago, count, date, dateTime, fullName, initials } from '../../lib/format'
import {
  Avatar,
  Button,
  ConfirmAction,
  EmptyState,
  Field,
  Modal,
  PageHeader,
  Panel,
  ROW,
  SearchInput,
  Segmented,
  SelectInput,
  SidePanel,
  StatusTag,
  TABLE,
  TD,
  TH,
  TextInput,
  notify,
  rowProps,
} from './ui'

type Role = AdminUser['role']
type Filter = 'all' | Role | 'deactivated'

const ROLE_LABEL: Record<Role, string> = { rider: 'Rider', driver: 'Driver', admin: 'Admin' }

export function AccountsPage({
  users,
  currentUserId,
  onChanged,
  openId,
  onOpenChange,
  onOpenDriver,
}: {
  users: AdminUser[]
  currentUserId: string
  onChanged: () => Promise<void>
  openId: string | null
  onOpenChange: (id: string | null) => void
  onOpenDriver: (id: string) => void
}) {
  const [filter, setFilter] = useState<Filter>('all')
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)
  const counts = useMemo(
    () => ({
      rider: users.filter((u) => u.role === 'rider' && !u.deactivated_at).length,
      driver: users.filter((u) => u.role === 'driver' && !u.deactivated_at).length,
      admin: users.filter((u) => u.role === 'admin' && !u.deactivated_at).length,
      deactivated: users.filter((u) => u.deactivated_at).length,
    }),
    [users],
  )
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    return users.filter((u) => {
      if (filter === 'deactivated' ? !u.deactivated_at : filter !== 'all' && (u.role !== filter || u.deactivated_at)) return false
      return !q || [fullName(u), u.email ?? '', u.phone_e164 ?? ''].some((s) => s.toLowerCase().includes(q))
    })
  }, [users, filter, query])
  const open = users.find((u) => u.id === openId) ?? null

  return (
    <div className="space-y-6">
      <PageHeader
        title="Accounts"
        meta={`${count(counts.rider)} riders · ${count(counts.driver)} drivers · ${count(counts.admin)} admins`}
        actions={
          <Button variant="solid" onClick={() => setCreating(true)}>
            <Plus className="size-4" /> Add account
          </Button>
        }
      />
      <Panel
        bodyClassName=""
        title={
          <Segmented
            label="Account filter"
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: 'all', label: 'All' },
              { value: 'rider', label: 'Riders' },
              { value: 'driver', label: 'Drivers' },
              { value: 'admin', label: 'Admins' },
              { value: 'deactivated', label: 'Deactivated', count: counts.deactivated },
            ]}
          />
        }
        action={<SearchInput label="Search accounts" value={query} onChange={setQuery} placeholder="Name, email, phone" />}
      >
        {rows.length === 0 ? (
          <EmptyState title="No accounts match" body="Try another filter or search." />
        ) : (
          <div className="overflow-x-auto">
            <table className={TABLE}>
              <thead>
                <tr>
                  <th className={TH}>Name</th>
                  <th className={TH}>Contact</th>
                  <th className={TH}>Role</th>
                  <th className={`${TH} text-right`}>Trips booked</th>
                  <th className={TH}>Joined</th>
                  <th className={TH}>Status</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((u) => (
                  <tr key={u.id} className={ROW} {...rowProps(() => onOpenChange(u.id), `Open ${fullName(u)}`)}>
                    <td className={TD}>
                      <div className="flex items-center gap-3">
                        <Avatar text={initials(u)} />
                        <span className="font-medium text-fg">
                          {fullName(u)}
                          {u.id === currentUserId && <span className="font-normal text-fg-3"> · you</span>}
                        </span>
                      </div>
                    </td>
                    <td className={TD}>
                      <div className="text-fg-2">{u.email ?? <span className="text-fg-3">No email</span>}</div>
                      <div className="font-mono text-xs text-fg-3">{u.phone_e164 ?? 'No phone'}</div>
                    </td>
                    <td className={`${TD} text-fg-2`}>{ROLE_LABEL[u.role]}</td>
                    <td className={`${TD} text-right font-mono text-fg-2 tabular-nums`}>{count(u.rides_count)}</td>
                    <td className={`${TD} whitespace-nowrap text-fg-2`}>{date(u.created_at)}</td>
                    <td className={TD}>
                      <StatusTag status={u.deactivated_at ? 'deactivated' : 'active'} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
      <AccountPanel user={open} isSelf={open?.id === currentUserId} onClose={() => onOpenChange(null)} onChanged={onChanged} onOpenDriver={onOpenDriver} />
      <AccountForm open={creating} onOpenChange={setCreating} onSaved={onChanged} />
    </div>
  )
}

type Pending = { kind: 'role'; role: Role } | { kind: 'deactivate' } | { kind: 'reactivate' }

function AccountPanel({ user, isSelf, onClose, onChanged, onOpenDriver }: { user: AdminUser | null; isSelf: boolean; onClose: () => void; onChanged: () => Promise<void>; onOpenDriver: (id: string) => void }) {
  const [activity, setActivity] = useState<AuditEntry[]>([])
  const [pending, setPending] = useState<Pending | null>(null)
  const [editing, setEditing] = useState(false)
  const [role, setRole] = useState<Role>('rider')
  useEffect(() => {
    setActivity([])
    if (user) {
      setRole(user.role)
      novaAdminApi.listAudit({ target_type: 'user', target_id: user.id, limit: 20 }).then(setActivity).catch(() => setActivity([]))
    }
  }, [user?.id, user?.role, user?.deactivated_at])

  if (!user) return <SidePanel open={false} onOpenChange={() => {}} title="" children={null} />
  const name = fullName(user)
  const done = async (msg: string) => {
    notify.done(msg)
    await onChanged()
  }

  return (
    <SidePanel
      open
      onOpenChange={(o) => !o && onClose()}
      eyebrow={`${ROLE_LABEL[user.role]} account`}
      title={name}
      meta={<StatusTag status={user.deactivated_at ? 'deactivated' : 'active'} />}
      footer={
        <>
          <Button onClick={() => setEditing(true)}>Edit details</Button>
          {!isSelf &&
            (user.deactivated_at ? (
              <Button variant="primary" onClick={() => setPending({ kind: 'reactivate' })}>
                Reactivate account
              </Button>
            ) : (
              <Button variant="danger" onClick={() => setPending({ kind: 'deactivate' })}>
                Deactivate account
              </Button>
            ))}
        </>
      }
    >
      <div className="space-y-8">
        {user.deactivated_at && (
          <p className="rounded-xl border border-bad/30 bg-bad/8 px-4 py-3 text-sm text-fg">
            Deactivated {dateTime(user.deactivated_at)}
            {user.deactivated_reason && <span className="text-fg-2"> · “{user.deactivated_reason}”</span>}
          </p>
        )}
        <dl className="grid grid-cols-1 gap-x-8 gap-y-4 rounded-xl border border-line p-5 sm:grid-cols-2">
          <Item label="Email" value={user.email ?? 'None'} sub={user.email ? (user.email_verified_at ? 'Verified' : 'Not verified') : undefined} />
          <Item label="Phone" value={user.phone_e164 ?? 'None'} mono sub={user.phone_e164 ? (user.phone_verified_at ? 'Verified' : 'Not verified') : undefined} />
          <Item label="Joined" value={date(user.created_at)} />
          <Item label="Trips booked" value={count(user.rides_count)} />
        </dl>

        {user.has_driver_profile && (
          <div className="flex items-center justify-between gap-4 rounded-xl border border-line p-4">
            <p className="text-sm text-fg-2">This account has a driver application.</p>
            <Button size="sm" onClick={() => onOpenDriver(user.id)}>
              Open application
            </Button>
          </div>
        )}

        <section className="space-y-3" aria-labelledby="role-title">
          <h3 id="role-title" className="text-sm font-medium text-fg">
            Role
          </h3>
          <div className="flex flex-wrap items-end gap-2">
            <div className="w-48">
              <SelectInput aria-label="Role" value={role} disabled={isSelf || !!user.deactivated_at} onChange={(e) => setRole(e.target.value as Role)}>
                <option value="rider">Rider</option>
                <option value="driver">Driver</option>
                <option value="admin">Admin</option>
              </SelectInput>
            </div>
            <Button disabled={role === user.role} onClick={() => setPending({ kind: 'role', role })}>
              Change role
            </Button>
          </div>
          <p className="text-xs text-fg-3">
            {isSelf ? "You can't change your own role." : 'One account has one role. Changing it signs the person out so the new access applies.'}
          </p>
        </section>

        <section className="space-y-3" aria-labelledby="acct-history">
          <h3 id="acct-history" className="text-sm font-medium text-fg">
            Admin activity on this account
          </h3>
          {activity.length === 0 ? (
            <p className="text-sm text-fg-3">Nothing yet.</p>
          ) : (
            <ol className="space-y-3">
              {activity.map((a) => (
                <li key={a.id} className="text-sm">
                  <p className="text-fg">{a.summary}</p>
                  <p className="text-xs text-fg-3">
                    {a.admin_name} · {ago(a.created_at)}
                    {a.reason && ` · “${a.reason}”`}
                  </p>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>

      <AccountForm open={editing} onOpenChange={setEditing} user={user} onSaved={onChanged} />
      {pending?.kind === 'role' && (
        <ConfirmAction
          open
          onOpenChange={(o) => !o && setPending(null)}
          title={`Make ${name} ${pending.role === 'admin' ? 'an admin' : `a ${pending.role}`}?`}
          description={
            pending.role === 'admin'
              ? `${user.first_name} gets full access to this console, including approving drivers and deactivating accounts.`
              : `${user.first_name} is signed out on every device and signs back in as a ${pending.role}.`
          }
          confirmLabel="Change role"
          tone={user.role === 'admin' ? 'danger' : 'primary'}
          formatError={formatError}
          onConfirm={async () => {
            await novaAdminApi.updateRole(user.id, pending.role)
            await done(`${name} is now ${pending.role === 'admin' ? 'an admin' : `a ${pending.role}`}`)
          }}
        />
      )}
      {pending?.kind === 'deactivate' && (
        <ConfirmAction
          open
          onOpenChange={(o) => !o && setPending(null)}
          tone="danger"
          title={`Deactivate ${name}?`}
          description={`${user.first_name} is signed out on every device and can't sign in.${user.role === 'driver' ? ' Their driver account is suspended too.' : ''} Their trips, documents and payouts are kept, and you can reactivate the account later.`}
          confirmLabel="Deactivate account"
          reason={{ label: 'Reason', placeholder: 'e.g. Repeated no-shows reported by drivers', required: true }}
          formatError={formatError}
          onConfirm={async (reason) => {
            await novaAdminApi.deactivateUser(user.id, reason)
            await done(`${name} is deactivated`)
          }}
        />
      )}
      {pending?.kind === 'reactivate' && (
        <ConfirmAction
          open
          onOpenChange={(o) => !o && setPending(null)}
          title={`Reactivate ${name}?`}
          description={`${user.first_name} can sign in again.${user.has_driver_profile ? ' Their driver account stays suspended until you reinstate it in Drivers.' : ''}`}
          confirmLabel="Reactivate"
          reason={{ label: 'Reason', placeholder: 'e.g. Issue resolved with support', required: true }}
          formatError={formatError}
          onConfirm={async (reason) => {
            await novaAdminApi.reactivateUser(user.id, reason)
            await done(`${name} can sign in again`)
          }}
        />
      )}
    </SidePanel>
  )
}

function AccountForm({ open, onOpenChange, user, onSaved }: { open: boolean; onOpenChange: (o: boolean) => void; user?: AdminUser; onSaved: () => Promise<void> }) {
  const [first, setFirst] = useState('')
  const [last, setLast] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [role, setRole] = useState<Role>('rider')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    if (!open) return
    setFirst(user?.first_name ?? '')
    setLast(user?.last_name ?? '')
    setEmail(user?.email ?? '')
    setPhone(user?.phone_e164 ?? '')
    setRole(user?.role ?? 'rider')
    setError(null)
  }, [open, user])
  const id = user ? 'edit' : 'new'
  return (
    <Modal
      open={open}
      onOpenChange={(o) => !busy && onOpenChange(o)}
      title={user ? `Edit ${fullName(user)}` : 'Add an account'}
      description={user ? 'Changes are saved in the activity log.' : 'The person signs in with this email or phone. Contact details you enter are treated as verified.'}
      footer={
        <>
          <Button variant="ghost" disabled={busy} onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            variant="solid"
            disabled={busy || !first.trim() || (!email.trim() && !phone.trim())}
            onClick={async () => {
              setBusy(true)
              setError(null)
              try {
                if (user) {
                  await novaAdminApi.updateUser(user.id, { first_name: first, last_name: last, email, phone_e164: phone })
                  notify.done('Account updated')
                } else {
                  await novaAdminApi.createUser({ first_name: first, last_name: last, email: email || undefined, phone_e164: phone || undefined, role })
                  notify.done(`${first} can sign in now`)
                }
                await onSaved()
                onOpenChange(false)
              } catch (err) {
                setError(formatError(err))
              } finally {
                setBusy(false)
              }
            }}
          >
            {busy ? 'Saving…' : user ? 'Save changes' : 'Add account'}
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-2 gap-3">
        <Field label="First name" htmlFor={`${id}-first`}>
          <TextInput id={`${id}-first`} value={first} onChange={(e) => setFirst(e.target.value)} autoComplete="off" />
        </Field>
        <Field label="Last name" htmlFor={`${id}-last`}>
          <TextInput id={`${id}-last`} value={last} onChange={(e) => setLast(e.target.value)} autoComplete="off" />
        </Field>
      </div>
      <Field label="Email" htmlFor={`${id}-email`}>
        <TextInput id={`${id}-email`} type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />
      </Field>
      <Field label="Mobile number" htmlFor={`${id}-phone`} hint="International format, e.g. +64 21 123 4567">
        <TextInput id={`${id}-phone`} type="tel" value={phone} onChange={(e) => setPhone(e.target.value.replace(/\s+/g, ''))} autoComplete="off" />
      </Field>
      {!user && (
        <Field label="Role" htmlFor="new-role">
          <SelectInput id="new-role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
            <option value="rider">Rider</option>
            <option value="driver">Driver</option>
            <option value="admin">Admin</option>
          </SelectInput>
        </Field>
      )}
      {error && (
        <p role="alert" className="text-sm text-bad">
          {error}
        </p>
      )}
    </Modal>
  )
}

function Item({ label, value, sub, mono }: { label: string; value: React.ReactNode; sub?: string; mono?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-fg-3">{label}</dt>
      <dd className={`mt-1 text-sm break-words text-fg ${mono ? 'font-mono' : ''}`}>{value}</dd>
      {sub && <dd className="text-xs text-fg-3">{sub}</dd>}
    </div>
  )
}
