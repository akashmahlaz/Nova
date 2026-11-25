import { useMemo, useRef, useState } from 'react'
import {
  Check,
  ExternalLink,
  FileText,
  HardDrive,
  Lock,
  Search,
  Trash2,
  Upload,
} from 'lucide-react'
import { NovaBadge, NovaButton, NovaCard, NovaDialog, NovaSegmented } from './ui'
import type { AdminMediaItem, AdminOverview } from '../../lib/api'

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

const CATEGORY_LABELS: Record<AdminMediaItem['category'], string> = {
  avatar: 'Profile Avatar',
  driver_licence: 'Driver Licence',
  driver_insurance: 'Insurance',
  vehicle_reg: 'Vehicle Rego',
  support_attachment: 'Support File',
  admin_asset: 'Admin Asset',
}

export function S3VaultPanel({
  media,
  overview,
  onUpload,
  onReview,
  onDelete,
}: {
  media: AdminMediaItem[]
  overview: AdminOverview | null
  onUpload: (file: File, category: AdminMediaItem['category']) => Promise<void>
  onReview: (id: string, status: 'verified' | 'rejected', reason?: string) => Promise<void>
  onDelete: (id: string) => Promise<void>
}) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | AdminMediaItem['status']>('all')
  const [categoryFilter, setCategoryFilter] = useState<'all' | AdminMediaItem['category']>('all')
  const [uploadCategory, setUploadCategory] = useState<AdminMediaItem['category']>('admin_asset')
  const [uploading, setUploading] = useState(false)
  const [previewItem, setPreviewItem] = useState<AdminMediaItem | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return media.filter((m) => {
      if (statusFilter !== 'all' && m.status !== statusFilter) return false
      if (categoryFilter !== 'all' && m.category !== categoryFilter) return false
      if (!q) return true
      return (
        m.owner_name.toLowerCase().includes(q) ||
        m.object_key.toLowerCase().includes(q) ||
        m.file_name.toLowerCase().includes(q)
      )
    })
  }, [media, query, statusFilter, categoryFilter])

  async function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      await onUpload(file, uploadCategory)
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <div className="space-y-4">
      <NovaCard className="p-5 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <HardDrive className="w-4 h-4 text-teal-600 dark:text-teal-400" />
            <h2 className="text-sm font-semibold text-zinc-950 dark:text-zinc-50">
              AWS S3 Signed Media Vault
            </h2>
            <NovaBadge tone="emerald">
              <Lock className="w-3 h-3" />
              SigV4 Private
            </NovaBadge>
          </div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 tabular-nums">
            Bucket{' '}
            <code className="font-mono text-[11px] text-zinc-950 dark:text-zinc-100">
              {overview?.s3_bucket ?? 'not configured'}
            </code>
            {' · '}Region{' '}
            <code className="font-mono text-[11px] text-zinc-950 dark:text-zinc-100">
              {overview?.s3_region ?? '—'}
            </code>
            {' · '}
            {media.length} objects ({formatBytes(overview?.media_bytes ?? 0)})
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={uploadCategory}
            onChange={(e) => setUploadCategory(e.target.value as AdminMediaItem['category'])}
            className="h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs font-medium text-zinc-950 dark:text-zinc-100"
          >
            {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,application/pdf"
            onChange={(e) => void handleFileSelect(e)}
            className="hidden"
          />
          <NovaButton
            disabled={uploading || !overview?.s3_configured}
            onClick={() => fileInputRef.current?.click()}
          >
            <Upload className="w-3.5 h-3.5" />
            {uploading ? 'Uploading to S3…' : 'Upload to S3'}
          </NovaButton>
        </div>
      </NovaCard>

      <NovaCard className="p-5 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="relative flex-1 max-w-xs">
            <Search className="w-3.5 h-3.5 text-zinc-400 dark:text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Filter by owner, key, filename…"
              className="w-full h-9 pl-8 pr-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-500"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={categoryFilter}
              onChange={(e) =>
                setCategoryFilter(e.target.value as 'all' | AdminMediaItem['category'])
              }
              className="h-9 px-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs font-medium text-zinc-950 dark:text-zinc-100"
            >
              <option value="all">All categories</option>
              {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
            <NovaSegmented
              value={statusFilter}
              onValueChange={setStatusFilter}
              options={[
                { value: 'all', label: 'All' },
                { value: 'uploaded', label: 'Needs Review' },
                { value: 'verified', label: 'Verified' },
                { value: 'rejected', label: 'Rejected' },
              ]}
            />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-zinc-200/80 dark:border-zinc-800 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                <th className="py-2.5 pr-4">Object</th>
                <th className="py-2.5 px-3">Owner</th>
                <th className="py-2.5 px-3">Category</th>
                <th className="py-2.5 px-3">Size</th>
                <th className="py-2.5 px-3">Status</th>
                <th className="py-2.5 pl-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/80 text-xs">
              {filtered.map((item) => (
                <tr key={item.id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/60">
                  <td className="py-3 pr-4">
                    <div className="flex items-center gap-3">
                      {item.view_url && item.content_type.startsWith('image/') ? (
                        <img
                          src={item.view_url}
                          alt={item.file_name || item.category}
                          className="w-9 h-9 rounded-lg object-cover border border-zinc-200 dark:border-zinc-800 bg-zinc-100 dark:bg-zinc-900"
                        />
                      ) : (
                        <div className="w-9 h-9 rounded-lg bg-zinc-100 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 flex items-center justify-center text-zinc-500 dark:text-zinc-400">
                          <FileText className="w-4 h-4" />
                        </div>
                      )}
                      <div className="min-w-0">
                        <div className="font-medium text-zinc-950 dark:text-zinc-100 truncate max-w-[220px]">
                          {item.file_name || item.object_key.split('/').pop()}
                        </div>
                        <div className="font-mono text-[10px] text-zinc-500 dark:text-zinc-400 truncate max-w-[220px]">
                          {item.object_key}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="py-3 px-3">
                    <div className="font-medium text-zinc-950 dark:text-zinc-100">
                      {item.owner_name}
                    </div>
                    <div className="text-[11px] text-zinc-500 dark:text-zinc-400 capitalize">
                      {item.owner_role}
                    </div>
                  </td>
                  <td className="py-3 px-3 text-zinc-600 dark:text-zinc-400">
                    {CATEGORY_LABELS[item.category]}
                  </td>
                  <td className="py-3 px-3 tabular-nums text-zinc-600 dark:text-zinc-400">
                    {formatBytes(item.size_bytes)}
                  </td>
                  <td className="py-3 px-3">
                    <NovaBadge
                      tone={
                        item.status === 'verified'
                          ? 'emerald'
                          : item.status === 'uploaded'
                            ? 'amber'
                            : item.status === 'rejected'
                              ? 'crimson'
                              : 'neutral'
                      }
                    >
                      {item.status === 'uploaded' ? 'Needs Review' : item.status}
                    </NovaBadge>
                  </td>
                  <td className="py-3 pl-3 text-right">
                    <div className="inline-flex items-center gap-1">
                      {item.view_url && (
                        <NovaButton
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            setRejectReason('')
                            setPreviewItem(item)
                          }}
                        >
                          Inspect
                        </NovaButton>
                      )}
                      {item.status !== 'verified' && item.status !== 'pending' && (
                        <NovaButton
                          variant="emerald"
                          size="sm"
                          onClick={() => onReview(item.id, 'verified')}
                          title="Verify document"
                        >
                          <Check className="w-3.5 h-3.5" />
                        </NovaButton>
                      )}
                      <NovaButton
                        variant="danger"
                        size="sm"
                        onClick={() => onDelete(item.id)}
                        title="Delete from S3 & DB"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </NovaButton>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-xs text-zinc-500 dark:text-zinc-400">
                    No S3 objects match your filter. Upload an asset or wait for Rider/Driver uploads.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </NovaCard>

      <NovaDialog
        open={Boolean(previewItem)}
        onOpenChange={(open) => {
          if (!open) setPreviewItem(null)
        }}
        title={previewItem?.file_name || (previewItem ? CATEGORY_LABELS[previewItem.category] : '')}
        subtitle={previewItem ? `s3://${previewItem.bucket}/${previewItem.object_key}` : undefined}
      >
        {previewItem?.view_url && (
          <div className="rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 p-3 flex items-center justify-center max-h-80 overflow-hidden">
            {previewItem.content_type.startsWith('image/') ? (
              <img
                src={previewItem.view_url}
                alt={previewItem.file_name}
                className="max-h-72 rounded-md object-contain"
              />
            ) : (
              <a
                href={previewItem.view_url}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-white dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 text-xs font-medium text-zinc-950 dark:text-zinc-100"
              >
                <ExternalLink className="w-3.5 h-3.5" />
                Open Signed PDF / File
              </a>
            )}
          </div>
        )}

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2 border-t border-zinc-200 dark:border-zinc-800">
          <input
            type="text"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="Rejection reason (optional)"
            className="flex-1 h-9 px-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs text-zinc-950 dark:text-zinc-100"
          />
          <div className="flex items-center gap-2">
            <NovaButton
              variant="danger"
              onClick={async () => {
                if (!previewItem) return
                await onReview(previewItem.id, 'rejected', rejectReason || 'Document unclear')
                setPreviewItem(null)
              }}
            >
              Reject
            </NovaButton>
            <NovaButton
              variant="emerald"
              onClick={async () => {
                if (!previewItem) return
                await onReview(previewItem.id, 'verified')
                setPreviewItem(null)
              }}
            >
              Verify Document
            </NovaButton>
          </div>
        </div>
      </NovaDialog>
    </div>
  )
}
