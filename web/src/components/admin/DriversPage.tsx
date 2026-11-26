import { useEffect, useMemo, useState } from 'react'
import { ExternalLink, FileText } from 'lucide-react'
import { type AdminDriver, type AdminDriverDetail, type AdminDriverDoc, type AuditEntry, novaAdminApi } from '../../lib/api'
import { formatError } from '../../lib/errors'
import { ago, count, date, dateTime, fullName, initials } from '../../lib/format'
import {
  Avatar,
  Button,
  ConfirmAction,
  EmptyState,
  PageHeader,
  Panel,
  Plate,
  ROW,
  SearchInput,
  Segmented,
  SidePanel,
  StatusTag,
  TABLE,
  TD,
  TH,
  Tag,
  notify,
  rowProps,
} from './ui'

type Filter = 'review' | 'incomplete' | 'approved' | 'suspended' | 'all'

export const readyForReview = (d: AdminDriver) => d.status === 'pending' && d.submitted_at !== null && d.docs_ready >= d.docs_required

/** "2019 White Toyota Prius · ABC123" → car and plate. */
export function splitVehicle(v: string | null): { car: string; plate: string | null } {
  if (!v) return { car: 'No car added', plate: null }
  const i = v.lastIndexOf(' · ')
  return i < 0 ? { car: v, plate: null } : { car: v.slice(0, i), plate: v.slice(i + 3) }
}

export function DriversPage({ drivers, onOpenChange }: { drivers: AdminDriver[]; onOpenChange: (id: string) => void }) {
  const [filter, setFilter] = useState<Filter>('review')
  const [query, setQuery] = useState('')

  const counts = useMemo(
    () => ({
      review: drivers.filter(readyForReview).length,
      incomplete: drivers.filter((d) => d.status === 'pending' && !readyForReview(d)).length,
      approved: drivers.filter((d) => d.status === 'approved').length,
      suspended: drivers.filter((d) => d.status === 'suspended').length,
    }),
    [drivers],
  )

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    return drivers.filter((d) => {
      if (filter === 'review' && !readyForReview(d)) return false
      if (filter === 'incomplete' && !(d.status === 'pending' && !readyForReview(d))) return false
      if ((filter === 'approved' || filter === 'suspended') && d.status !== filter) return false
      if (!q) return true
      return [fullName(d), d.licence_number, d.vehicle ?? '', d.phone_e164 ?? ''].some((s) => s.toLowerCase().includes(q))
    })
  }, [drivers, filter, query])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Drivers"
        meta={`${count(counts.approved)} approved · ${count(counts.review)} ready to review · ${count(counts.incomplete)} still applying`}
      />
      <Panel
        bodyClassName=""
        title={
          <Segmented
            label="Driver filter"
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: 'review', label: 'Ready to review', count: counts.review },
              { value: 'incomplete', label: 'Still applying', count: counts.incomplete },
              { value: 'approved', label: 'Approved' },
              { value: 'suspended', label: 'Suspended', count: counts.suspended },
              { value: 'all', label: 'All' },
            ]}
          />
        }
        action={<SearchInput label="Search drivers" value={query} onChange={setQuery} placeholder="Name, plate, licence, phone" />}
      >
        {rows.length === 0 ? (
          <EmptyState
            title={filter === 'review' ? 'No applications waiting' : 'No drivers here'}
            body={filter === 'review' ? 'New applications appear here once a driver has uploaded every document and submitted.' : 'Try another filter or search.'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className={TABLE}>
              <thead>
                <tr>
                  <th className={TH}>Driver</th>
                  <th className={TH}>Car</th>
                  <th className={TH}>Documents</th>
                  <th className={TH}>Applied</th>
                  <th className={TH}>Status</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => {
                  const v = splitVehicle(d.vehicle)
                  return (
                    <tr key={d.user_id} className={ROW} {...rowProps(() => onOpenChange(d.user_id), `Review ${fullName(d)}`)}>
                      <td className={TD}>
                        <div className="flex items-center gap-3">
                          <Avatar text={initials(d)} />
                          <div className="min-w-0">
                            <div className="truncate font-medium text-fg">{fullName(d)}</div>
                            <div className="font-mono text-xs text-fg-3">{d.phone_e164 ?? 'No phone'}</div>
                          </div>
                        </div>
                      </td>
                      <td className={TD}>
                        <div className="flex items-center gap-2.5">
                          {v.plate && <Plate plate={v.plate} />}
                          <span className="truncate text-fg-2">{v.car}</span>
                        </div>
                      </td>
                      <td className={TD}>
                        <DocsMeter ready={d.docs_ready} required={d.docs_required} />
                      </td>
                      <td className={`${TD} text-fg-2`}>{d.submitted_at ? ago(d.submitted_at) : <span className="text-fg-3">Not submitted</span>}</td>
                      <td className={TD}>
                        <StatusTag status={d.status} />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}

function DocsMeter({ ready, required }: { ready: number; required: number }) {
  const complete = ready >= required
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex gap-0.5" aria-hidden="true">
        {Array.from({ length: required }, (_, i) => (
          <span key={i} className={`h-1.5 w-3 rounded-full ${i < ready ? (complete ? 'bg-good' : 'bg-fg-2') : 'bg-line-strong'}`} />
        ))}
      </div>
      <span className="font-mono text-xs text-fg-2">
        {ready} of {required}
      </span>
    </div>
  )
}

type Pending =
  | { kind: 'approve' }
  | { kind: 'send-back' }
  | { kind: 'suspend' }
  | { kind: 'reinstate' }
  | { kind: 'verify-doc'; doc: AdminDriverDoc }
  | { kind: 'reject-doc'; doc: AdminDriverDoc }

/** One application, end to end. The documents are the centre of the screen. */
export function DriverReview({ userId, onClose, onChanged }: { userId: string | null; onClose: () => void; onChanged: () => Promise<void> }) {
  const [detail, setDetail] = useState<AdminDriverDetail | null>(null)
  const [activity, setActivity] = useState<AuditEntry[]>([])
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [pending, setPending] = useState<Pending | null>(null)

  async function load(id: string) {
    try {
      const [d, a] = await Promise.all([novaAdminApi.getDriver(id), novaAdminApi.listAudit({ target_type: 'driver', target_id: id, limit: 20 })])
      setDetail(d)
      setActivity(a)
      setError(null)
      setSelected((cur) => cur ?? d.documents.find((x) => x.status === 'uploaded')?.category ?? d.documents[0]?.category ?? null)
    } catch (err) {
      setError(formatError(err))
    }
  }

  useEffect(() => {
    setDetail(null)
    setActivity([])
    setSelected(null)
    if (userId) void load(userId)
  }, [userId])

  const d = detail?.driver
  const name = d ? fullName(d) : ''
  const docs = detail?.documents ?? []
  const doc = docs.find((x) => x.category === selected) ?? null
  const missing = docs.filter((x) => x.required && (x.status === null || x.status === 'rejected' || x.status === 'pending')).map((x) => x.name)
  if (detail && !detail.vehicle) missing.push('a car')

  async function after(message: string) {
    notify.done(message)
    if (userId) await load(userId)
    await onChanged()
  }

  const footer = d && detail && (
    <>
      {!detail.ready_for_approval && d.status === 'pending' && <p className="mr-auto text-[13px] text-warn">Still needed: {missing.join(', ')}</p>}
      {d.status === 'pending' && (
        <>
          <Button onClick={() => setPending({ kind: 'send-back' })}>Send back for changes</Button>
          <Button variant="primary" disabled={!detail.ready_for_approval} onClick={() => setPending({ kind: 'approve' })}>
            Approve driver
          </Button>
        </>
      )}
      {d.status === 'approved' && (
        <Button variant="danger" onClick={() => setPending({ kind: 'suspend' })}>
          Suspend driver
        </Button>
      )}
      {d.status === 'suspended' && (
        <>
          <Button onClick={() => setPending({ kind: 'send-back' })}>Move back to review</Button>
          <Button variant="primary" disabled={!detail.ready_for_approval} onClick={() => setPending({ kind: 'reinstate' })}>
            Reinstate driver
          </Button>
        </>
      )}
    </>
  )

  return (
    <SidePanel
      open={userId !== null}
      onOpenChange={(o) => !o && onClose()}
      eyebrow="Driver application"
      title={d ? name : 'Loading…'}
      meta={d && <span className="font-mono text-[13px]">{[d.phone_e164, d.email].filter(Boolean).join(' · ')}</span>}
      width="max-w-4xl"
      footer={footer}
    >
      {error && (
        <p role="alert" className="mb-4 rounded-xl border border-bad/30 bg-bad/8 px-4 py-3 text-sm text-bad">
          {error}
        </p>
      )}
      {d && detail && (
        <div className="space-y-8">
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
            <StatusTag status={d.status} />
            <span className="text-fg-2">{d.submitted_at ? `Submitted ${dateTime(d.submitted_at)}` : 'Not submitted yet'}</span>
            <span className="text-fg-2">{count(detail.completed_trips)} completed trips</span>
            {d.review_note && <span className="text-fg-2">Last note: “{d.review_note}”</span>}
          </div>

          <section aria-labelledby="docs-title" className="space-y-3">
            <h3 id="docs-title" className="text-sm font-medium text-fg">
              Documents
            </h3>
            <div className="grid gap-4 md:grid-cols-[220px_1fr]">
              <ul className="space-y-1" aria-label="Documents">
                {docs.map((x) => (
                  <li key={x.category}>
                    <button
                      type="button"
                      onClick={() => setSelected(x.category)}
                      aria-current={x.category === selected ? 'true' : undefined}
                      className={`flex w-full cursor-pointer flex-col items-start gap-1 rounded-[10px] px-3 py-2.5 text-left transition-colors ${x.category === selected ? 'bg-sunken ring-1 ring-line-strong' : 'hover:bg-sunken'}`}
                    >
                      <span className="text-sm font-medium text-fg">
                        {x.name}
                        {!x.required && <span className="font-normal text-fg-3"> · optional</span>}
                      </span>
                      {x.status ? <StatusTag status={x.status} /> : <Tag>Not uploaded</Tag>}
                    </button>
                  </li>
                ))}
              </ul>
              <DocumentViewer doc={doc} ownerFirstName={d.first_name} onVerify={(x) => setPending({ kind: 'verify-doc', doc: x })} onReject={(x) => setPending({ kind: 'reject-doc', doc: x })} />
            </div>
          </section>

          <section aria-labelledby="facts-title" className="space-y-3">
            <h3 id="facts-title" className="text-sm font-medium text-fg">
              Licence, car and payouts
            </h3>
            <dl className="grid grid-cols-1 gap-x-8 gap-y-4 rounded-xl border border-line p-5 sm:grid-cols-2 lg:grid-cols-3">
              <Fact label="Licence number" value={<span className="font-mono">{d.licence_number}</span>} />
              <Fact label="Licence expiry" value={d.licence_expiry ? date(d.licence_expiry) : 'Not given'} />
              <Fact label="Applied" value={date(d.created_at)} />
              <Fact
                label="Car"
                value={
                  detail.vehicle ? (
                    <span className="flex flex-wrap items-center gap-2">
                      <Plate plate={detail.vehicle.plate} />
                      {detail.vehicle.year} {detail.vehicle.color} {detail.vehicle.make} {detail.vehicle.model}
                    </span>
                  ) : (
                    'No car added'
                  )
                }
              />
              <Fact
                label="Seats and access"
                value={detail.vehicle ? [`${detail.vehicle.seats} seats`, detail.vehicle.is_electric && 'Electric', detail.vehicle.wheelchair_accessible && 'Wheelchair accessible'].filter(Boolean).join(' · ') : '—'}
              />
              <Fact label="Ride types" value={detail.products.length ? detail.products.join(', ') : 'None yet'} />
              <Fact label="Bank account" value={detail.payout ? <span className="font-mono">{detail.payout.account_number}</span> : 'Not added'} />
              <Fact label="Account name" value={detail.payout?.account_name ?? '—'} />
              <Fact
                label="Tax"
                value={detail.payout ? `${detail.payout.tax_number || 'No IRD number'} · ${detail.payout.gst_registered ? 'GST registered' : 'Not GST registered'} · paid ${detail.payout.payout_schedule}` : '—'}
              />
            </dl>
          </section>

          <section aria-labelledby="history-title" className="space-y-3">
            <h3 id="history-title" className="text-sm font-medium text-fg">
              Review history
            </h3>
            {activity.length === 0 ? (
              <p className="text-sm text-fg-3">No admin actions on this application yet.</p>
            ) : (
              <ol className="space-y-3">
                {activity.map((a) => (
                  <li key={a.id} className="flex gap-3 text-sm">
                    <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-fg-3" />
                    <div>
                      <p className="text-fg">{a.summary}</p>
                      <p className="text-xs text-fg-3">
                        {a.admin_name} · {dateTime(a.created_at)}
                        {a.reason && ` · “${a.reason}”`}
                      </p>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      )}

      {d && pending && (
        <ReviewDialogs
          pending={pending}
          name={name}
          firstName={d.first_name}
          onClose={() => setPending(null)}
          run={async (reason) => {
            const id = d.user_id
            switch (pending.kind) {
              case 'approve':
              case 'reinstate':
                await novaAdminApi.updateDriverStatus(id, 'approved', reason || undefined)
                return after(`${name} can drive now`)
              case 'send-back':
                await novaAdminApi.updateDriverStatus(id, 'pending', reason)
                return after(`Sent back to ${d.first_name}`)
              case 'suspend':
                await novaAdminApi.updateDriverStatus(id, 'suspended', reason)
                return after(`${name} is suspended`)
              case 'verify-doc':
                await novaAdminApi.reviewMedia(pending.doc.media_id!, 'verified')
                return after(`${pending.doc.name} verified`)
              case 'reject-doc':
                await novaAdminApi.reviewMedia(pending.doc.media_id!, 'rejected', reason)
                return after(`Asked ${d.first_name} for a new ${pending.doc.name.toLowerCase()}`)
            }
          }}
        />
      )}
    </SidePanel>
  )
}

function ReviewDialogs({ pending, name, firstName, onClose, run }: { pending: Pending; name: string; firstName: string; onClose: () => void; run: (reason: string) => Promise<void> }) {
  const common = { open: true, onOpenChange: (o: boolean) => !o && onClose(), onConfirm: run, formatError }
  switch (pending.kind) {
    case 'approve':
      return (
        <ConfirmAction
          {...common}
          title={`Approve ${name} to drive?`}
          description={`${firstName} can go online straight away and will start getting trip requests. Their uploaded documents are marked verified.`}
          confirmLabel="Approve driver"
        />
      )
    case 'reinstate':
      return (
        <ConfirmAction
          {...common}
          title={`Reinstate ${name}?`}
          description={`${firstName} can go online again and get trip requests.`}
          confirmLabel="Reinstate driver"
          reason={{ label: 'Reason', placeholder: 'e.g. Insurance renewed and checked', required: true }}
        />
      )
    case 'send-back':
      return (
        <ConfirmAction
          {...common}
          title={`Send the application back to ${firstName}?`}
          description={`${firstName} sees your note in the Nova Driver app and can fix it, then submit again.`}
          confirmLabel="Send back"
          reason={{ label: `Note to ${firstName}`, placeholder: 'e.g. Your P endorsement photo is cut off. Please retake it.', required: true, hint: `Shown to ${firstName} and saved in the activity log.` }}
        />
      )
    case 'suspend':
      return (
        <ConfirmAction
          {...common}
          tone="danger"
          title={`Suspend ${name}?`}
          description={`${firstName} goes offline now and can't get trips until an admin reinstates them. Trips already finished aren't affected.`}
          confirmLabel="Suspend driver"
          reason={{ label: `Reason, shown to ${firstName}`, placeholder: 'e.g. Insurance expired on 20 November', required: true }}
        />
      )
    case 'verify-doc':
      return (
        <ConfirmAction
          {...common}
          title={`Mark ${pending.doc.name.toLowerCase()} as verified?`}
          description="Only verify if you can read it clearly and it belongs to this driver."
          confirmLabel="Verify"
        />
      )
    case 'reject-doc':
      return (
        <ConfirmAction
          {...common}
          title={`Ask ${firstName} for a new ${pending.doc.name.toLowerCase()}?`}
          description={`${firstName} gets a notification with your reason and can upload it again from the app.`}
          confirmLabel="Send back"
          reason={{ label: 'What needs fixing', placeholder: "e.g. The photo is blurry and the expiry date can't be read", required: true, hint: `Shown to ${firstName}.` }}
        />
      )
  }
}

/** The review surface: the driver's own photo, large, on a calm backdrop. */
function DocumentViewer({ doc, ownerFirstName, onVerify, onReject }: { doc: AdminDriverDoc | null; ownerFirstName: string; onVerify: (d: AdminDriverDoc) => void; onReject: (d: AdminDriverDoc) => void }) {
  if (!doc) return <div className="min-h-80 rounded-2xl bg-sunken" />
  return (
    <div className="overflow-hidden rounded-2xl border border-line bg-sunken">
      <div className="flex min-h-80 items-center justify-center p-4">
        {doc.view_url ? (
          doc.content_type?.startsWith('image/') ? (
            <a href={doc.view_url} target="_blank" rel="noreferrer" className="block" title="Open full size">
              <img src={doc.view_url} alt={`${doc.name} uploaded by ${ownerFirstName}`} className="max-h-[420px] w-auto rounded-lg shadow-[0_8px_24px_rgb(0_0_0/0.18)]" />
            </a>
          ) : (
            <a href={doc.view_url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 rounded-[10px] border border-line-strong bg-surface px-4 py-3 text-sm font-medium text-fg hover:bg-raised">
              <FileText className="size-4" /> Open PDF <ExternalLink className="size-3.5 text-fg-3" />
            </a>
          )
        ) : (
          <p className="max-w-xs text-center text-sm text-fg-3">{ownerFirstName} hasn't uploaded this yet.</p>
        )}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line bg-surface px-4 py-3">
        <div className="min-w-0">
          <p className="text-sm font-medium text-fg">{doc.name}</p>
          <p className="text-xs text-fg-3">
            {doc.status === 'rejected' && doc.rejection_reason ? `Sent back: ${doc.rejection_reason}` : doc.uploaded_at ? `Uploaded ${dateTime(doc.uploaded_at)}` : doc.description}
          </p>
        </div>
        {doc.media_id && doc.status === 'uploaded' && (
          <div className="flex gap-2">
            <Button size="sm" onClick={() => onReject(doc)}>
              Ask for a new one
            </Button>
            <Button size="sm" variant="solid" onClick={() => onVerify(doc)}>
              Verify
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}

function Fact({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-fg-3">{label}</dt>
      <dd className="mt-1 text-sm break-words text-fg">{value}</dd>
    </div>
  )
}
