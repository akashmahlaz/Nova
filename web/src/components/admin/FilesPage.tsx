import { useMemo, useRef, useState } from 'react'
import { ExternalLink, FileText, Upload } from 'lucide-react'
import { type AdminMediaItem, type AdminOverview, novaAdminApi } from '../../lib/api'
import { formatError } from '../../lib/errors'
import { ago, bytes, count, dateTime } from '../../lib/format'
import { Button, ConfirmAction, DataTable, Modal, PageHeader, Segmented, SelectInput, StatusTag, notify } from './ui'

export const CATEGORY_LABEL: Record<AdminMediaItem['category'], string> = {
  avatar: 'Profile photo',
  driver_licence: 'Driving licence',
  driver_endorsement: 'P endorsement',
  driver_photo: 'Driver photo',
  driver_insurance: 'Vehicle insurance',
  vehicle_reg: 'Vehicle rego',
  support_attachment: 'Support file',
  admin_asset: 'Admin file',
}

/** Only files the team itself adds can be deleted; anything a person uploaded is a record. */
const DELETABLE: AdminMediaItem['category'][] = ['admin_asset', 'support_attachment']

type Filter = 'review' | 'verified' | 'rejected' | 'all'

export function FilesPage({ media, overview, onChanged, onOpenDriver }: { media: AdminMediaItem[]; overview: AdminOverview | null; onChanged: () => Promise<void>; onOpenDriver: (id: string) => void }) {
  const [filter, setFilter] = useState<Filter>('review')
  const [category, setCategory] = useState<'all' | AdminMediaItem['category']>('all')
  const [query, setQuery] = useState('')
  const [preview, setPreview] = useState<AdminMediaItem | null>(null)
  const [pending, setPending] = useState<{ kind: 'verify' | 'reject' | 'delete'; item: AdminMediaItem } | null>(null)
  const [uploadCategory, setUploadCategory] = useState<AdminMediaItem['category']>('admin_asset')
  const [uploading, setUploading] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  const toReview = media.filter((m) => m.status === 'uploaded').length
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    return media.filter((m) => {
      if (filter === 'review' && m.status !== 'uploaded') return false
      if ((filter === 'verified' || filter === 'rejected') && m.status !== filter) return false
      if (category !== 'all' && m.category !== category) return false
      return !q || [m.owner_name, m.file_name].some((s) => s.toLowerCase().includes(q))
    })
  }, [media, filter, category, query])

  async function upload(file: File) {
    setUploading(true)
    try {
      await novaAdminApi.uploadToS3(file, uploadCategory)
      notify.done(`${file.name} uploaded`)
      await onChanged()
    } catch (err) {
      notify.failed("Couldn't upload the file", formatError(err))
    } finally {
      setUploading(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Files"
        meta={
          overview?.s3_configured
            ? `${count(overview.media_total)} files · ${bytes(overview.media_bytes)} in private storage (${overview.s3_region})`
            : "File storage isn't configured on the server"
        }
        actions={
          <>
            <SelectInput aria-label="Type of file to upload" value={uploadCategory} onChange={(e) => setUploadCategory(e.target.value as AdminMediaItem['category'])} className="w-40">
              {DELETABLE.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABEL[c]}
                </option>
              ))}
            </SelectInput>
            <input ref={input} type="file" className="hidden" accept="image/jpeg,image/png,image/webp,application/pdf" onChange={(e) => e.target.files?.[0] && void upload(e.target.files[0])} />
            <Button variant="solid" disabled={uploading || !overview?.s3_configured} onClick={() => input.current?.click()}>
              <Upload className="size-4" /> {uploading ? 'Uploading…' : 'Upload file'}
            </Button>
          </>
        }
      />
      <DataTable
        id="files"
        label="Files"
        tabs={
          <Segmented
            label="File filter"
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: 'review', label: 'Needs review', count: toReview },
              { value: 'verified', label: 'Verified' },
              { value: 'rejected', label: 'Sent back' },
              { value: 'all', label: 'All' },
            ]}
          />
        }
        search={{ value: query, onChange: setQuery, placeholder: 'Owner or file name' }}
        toolbar={
          <SelectInput aria-label="File type" value={category} onChange={(e) => setCategory(e.target.value as typeof category)} className="w-44">
            <option value="all">All types</option>
            {Object.entries(CATEGORY_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </SelectInput>
        }
        rows={rows}
        rowKey={(m) => m.id}
        onRowClick={setPreview}
        rowLabel={(m) => `Open ${m.file_name}`}
        empty={
          filter === 'review'
            ? { title: 'Nothing to review', body: 'Documents drivers upload appear here until someone checks them.' }
            : { title: 'No files match', body: 'Try another filter or search.' }
        }
        summary={`${count(rows.length)} ${rows.length === 1 ? 'file' : 'files'} shown`}
        columns={[
          {
            id: 'file',
            header: 'File',
            hideable: false,
            className: 'max-w-[280px]',
            cell: (m) => (
              <div className="flex items-center gap-3">
                <Thumb item={m} />
                <span className="truncate text-fg">{m.file_name}</span>
              </div>
            ),
          },
          { id: 'owner', header: 'Belongs to', className: 'text-fg-2', cell: (m) => m.owner_name },
          { id: 'type', header: 'Type', className: 'text-fg-2', cell: (m) => CATEGORY_LABEL[m.category] },
          { id: 'size', header: 'Size', align: 'right', className: 'font-mono text-fg-2 tabular-nums', cell: (m) => bytes(m.size_bytes) },
          { id: 'uploaded', header: 'Uploaded', className: 'whitespace-nowrap text-fg-2', cell: (m) => ago(m.created_at) },
          { id: 'status', header: 'Status', cell: (m) => <StatusTag status={m.status} /> },
        ]}
      />

      {preview && (
        <Modal
          open
          onOpenChange={(o) => !o && setPreview(null)}
          width="max-w-3xl"
          title={CATEGORY_LABEL[preview.category]}
          description={`${preview.owner_name} · ${preview.file_name} · uploaded ${dateTime(preview.created_at)}`}
          footer={
            <>
              {DELETABLE.includes(preview.category) && (
                <Button variant="danger" className="mr-auto" onClick={() => setPending({ kind: 'delete', item: preview })}>
                  Delete file
                </Button>
              )}
              {preview.owner_role === 'driver' && (
                <Button
                  onClick={() => {
                    setPreview(null)
                    onOpenDriver(preview.owner_user_id)
                  }}
                >
                  Open driver application
                </Button>
              )}
              {preview.status === 'uploaded' && !DELETABLE.includes(preview.category) && (
                <>
                  <Button onClick={() => setPending({ kind: 'reject', item: preview })}>Ask for a new one</Button>
                  <Button variant="solid" onClick={() => setPending({ kind: 'verify', item: preview })}>
                    Verify
                  </Button>
                </>
              )}
            </>
          }
        >
          <div className="flex min-h-72 items-center justify-center rounded-xl bg-sunken p-4">
            {preview.view_url ? (
              preview.content_type.startsWith('image/') ? (
                <img src={preview.view_url} alt={`${CATEGORY_LABEL[preview.category]} from ${preview.owner_name}`} className="max-h-[60vh] w-auto rounded-lg shadow-[0_8px_24px_rgb(0_0_0/0.18)]" />
              ) : (
                <a href={preview.view_url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 rounded-[10px] border border-line-strong bg-surface px-4 py-3 text-sm font-medium text-fg">
                  <FileText className="size-4" /> Open PDF <ExternalLink className="size-3.5 text-fg-3" />
                </a>
              )
            ) : (
              <p className="text-sm text-fg-3">The upload didn't finish, so there's nothing to show.</p>
            )}
          </div>
          {preview.rejection_reason && <p className="text-sm text-fg-2">Sent back: {preview.rejection_reason}</p>}
        </Modal>
      )}

      {pending?.kind === 'verify' && (
        <ConfirmAction
          open
          onOpenChange={(o) => !o && setPending(null)}
          title={`Verify ${pending.item.owner_name}'s ${CATEGORY_LABEL[pending.item.category].toLowerCase()}?`}
          description="Only verify if you can read it clearly and it belongs to this person."
          confirmLabel="Verify"
          formatError={formatError}
          onConfirm={async () => {
            await novaAdminApi.reviewMedia(pending.item.id, 'verified')
            notify.done('Verified')
            setPreview(null)
            await onChanged()
          }}
        />
      )}
      {pending?.kind === 'reject' && (
        <ConfirmAction
          open
          onOpenChange={(o) => !o && setPending(null)}
          title={`Ask ${pending.item.owner_name} for a new one?`}
          description="They get a notification with your reason and can upload it again from the app."
          confirmLabel="Send back"
          reason={{ label: 'What needs fixing', placeholder: "e.g. The photo is blurry and the expiry date can't be read", required: true }}
          formatError={formatError}
          onConfirm={async (reason) => {
            await novaAdminApi.reviewMedia(pending.item.id, 'rejected', reason)
            notify.done('Sent back')
            setPreview(null)
            await onChanged()
          }}
        />
      )}
      {pending?.kind === 'delete' && (
        <ConfirmAction
          open
          onOpenChange={(o) => !o && setPending(null)}
          tone="danger"
          title={`Delete ${pending.item.file_name}?`}
          description="The file is removed from storage for good. This can't be undone."
          confirmLabel="Delete file"
          formatError={formatError}
          onConfirm={async () => {
            await novaAdminApi.deleteMedia(pending.item.id)
            notify.done('File deleted')
            setPreview(null)
            await onChanged()
          }}
        />
      )}
    </div>
  )
}

function Thumb({ item }: { item: AdminMediaItem }) {
  if (item.view_url && item.content_type.startsWith('image/'))
    return <img src={item.view_url} alt="" loading="lazy" className="size-9 shrink-0 rounded-lg object-cover ring-1 ring-line" />
  return (
    <span aria-hidden="true" className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg bg-sunken text-fg-3 ring-1 ring-line">
      <FileText className="size-4" />
    </span>
  )
}
