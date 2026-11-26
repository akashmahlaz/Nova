/**
 * Nova Ops primitives. Quiet graphite chrome; Volt only for the primary action and live state.
 * Behaviour (focus, keyboard, ARIA) comes from @ui-sdk/react; the look lives here.
 */
import { useEffect, useState, type ReactNode } from 'react'
import { Button as SdkButton } from '@ui-sdk/react/button'
import { Dialog, DialogBackdrop, DialogContent, DialogDescription, DialogTitle } from '@ui-sdk/react/dialog'
import { Drawer, DrawerBackdrop, DrawerContent, DrawerTitle } from '@ui-sdk/react/drawer'
import { SegmentedControl, SegmentedControlItem } from '@ui-sdk/react/segmented-control'
import { Toast, ToastClose, ToastDescription, ToastRegion, ToastTitle, toast } from '@ui-sdk/react/toast'
import { CircleAlert, CircleCheck, Monitor, Moon, Search, Sun, X } from 'lucide-react'

export const ICON = 'size-4 shrink-0'

// ---------- surfaces ----------

export function Panel({
  title,
  meta,
  action,
  children,
  className = '',
  bodyClassName = 'p-5',
}: {
  title?: ReactNode
  meta?: ReactNode
  action?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
}) {
  return (
    <section className={`rounded-2xl border border-line bg-surface ${className}`}>
      {(title || action) && (
        <header className="flex min-h-14 items-center justify-between gap-4 border-b border-line px-5 py-3">
          <div className="min-w-0">
            {title && <h2 className="text-sm font-medium text-fg">{title}</h2>}
            {meta && <p className="text-xs text-fg-3">{meta}</p>}
          </div>
          {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
        </header>
      )}
      <div className={bodyClassName}>{children}</div>
    </section>
  )
}

export function PageHeader({ title, meta, actions }: { title: string; meta?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-[28px] leading-9 font-medium text-fg">{title}</h1>
        {meta && <p className="mt-1 text-sm text-fg-2">{meta}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export function EmptyState({ title, body, action }: { title: string; body?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
      <p className="text-sm font-medium text-fg">{title}</p>
      {body && <p className="max-w-sm text-sm text-fg-3">{body}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}

// ---------- actions ----------

type ButtonVariant = 'primary' | 'solid' | 'secondary' | 'ghost' | 'danger' | 'danger-solid'

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-volt text-on-volt hover:brightness-95',
  solid: 'bg-fg text-surface hover:opacity-90',
  secondary: 'border border-line-strong bg-surface text-fg hover:bg-sunken',
  ghost: 'text-fg-2 hover:bg-sunken hover:text-fg',
  danger: 'border border-line-strong bg-surface text-bad hover:bg-bad/8',
  'danger-solid': 'bg-bad text-white hover:opacity-90',
}

export function Button({
  children,
  variant = 'secondary',
  size = 'md',
  className = '',
  ...props
}: React.ComponentProps<typeof SdkButton> & { variant?: ButtonVariant; size?: 'sm' | 'md' }) {
  const sizes = { sm: 'h-8 px-3 text-[13px] rounded-lg gap-1.5', md: 'h-9 px-4 text-sm rounded-[10px] gap-2' }
  return (
    <SdkButton
      {...props}
      className={`inline-flex shrink-0 cursor-pointer items-center justify-center font-medium whitespace-nowrap transition-[background-color,color,opacity,filter] duration-150 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-45 data-pending:opacity-60 ${BUTTON_VARIANTS[variant]} ${sizes[size]} ${className}`}
    >
      {children}
    </SdkButton>
  )
}

export function IconButton({ label, children, className = '', ...props }: React.ComponentProps<typeof SdkButton> & { label: string }) {
  return (
    <SdkButton
      {...props}
      aria-label={label}
      title={label}
      className={`inline-flex size-9 cursor-pointer items-center justify-center rounded-[10px] text-fg-2 transition-colors hover:bg-sunken hover:text-fg disabled:opacity-45 ${className}`}
    >
      {children}
    </SdkButton>
  )
}

export function Kbd({ children }: { children: ReactNode }) {
  return (
    <kbd className="inline-flex h-5 min-w-5 items-center justify-center rounded-[5px] border border-line-strong bg-surface px-1 font-mono text-[11px] text-fg-3">
      {children}
    </kbd>
  )
}

// ---------- status ----------

export type Tone = 'neutral' | 'good' | 'warn' | 'bad' | 'live'

const TONE_DOT: Record<Tone, string> = {
  neutral: 'bg-fg-3',
  good: 'bg-good',
  warn: 'bg-warn',
  bad: 'bg-bad',
  live: 'bg-volt ring-2 ring-volt/30',
}
const TONE_TEXT: Record<Tone, string> = {
  neutral: 'text-fg-2',
  good: 'text-good',
  warn: 'text-warn',
  bad: 'text-bad',
  live: 'text-fg',
}

/** Status as a dot and a word. Colour carries meaning only. */
export function Tag({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1.5 text-[13px] whitespace-nowrap ${TONE_TEXT[tone]}`}>
      <span aria-hidden="true" className={`size-1.5 rounded-full ${TONE_DOT[tone]}`} />
      {children}
    </span>
  )
}

const STATUS: Record<string, [string, Tone]> = {
  // trips
  requested: ['Finding driver', 'live'],
  accepted: ['Driver on the way', 'live'],
  arriving: ['At pickup', 'live'],
  in_progress: ['On trip', 'live'],
  completed: ['Completed', 'good'],
  cancelled: ['Cancelled', 'neutral'],
  no_drivers: ['No driver found', 'bad'],
  scheduled: ['Scheduled', 'neutral'],
  // drivers
  pending: ['In review', 'warn'],
  approved: ['Approved', 'good'],
  suspended: ['Suspended', 'bad'],
  // documents
  uploaded: ['Needs review', 'warn'],
  verified: ['Verified', 'good'],
  rejected: ['Sent back', 'bad'],
  // accounts
  active: ['Active', 'good'],
  deactivated: ['Deactivated', 'bad'],
}

export function StatusTag({ status }: { status: string }) {
  const [label, tone] = STATUS[status] ?? [status.replace(/_/g, ' '), 'neutral']
  return <Tag tone={tone}>{label}</Tag>
}

export const isLiveTrip = (status: string) => ['requested', 'accepted', 'arriving', 'in_progress'].includes(status)

/** NZ number plate: the one detail every vehicle shows the same way. */
export function Plate({ plate }: { plate: string }) {
  return (
    <span className="inline-flex h-6 items-center rounded-[5px] border border-[#111114]/70 bg-[var(--plate)] px-1.5 font-mono text-[12px] font-medium tracking-[0.08em] text-[var(--plate-ink)] uppercase">
      {plate}
    </span>
  )
}

export function Avatar({ text, size = 'md' }: { text: string; size?: 'sm' | 'md' | 'lg' }) {
  const s = { sm: 'size-7 text-[11px]', md: 'size-8 text-xs', lg: 'size-12 text-base' }[size]
  return (
    <span aria-hidden="true" className={`inline-flex shrink-0 items-center justify-center rounded-full bg-sunken font-medium text-fg-2 ring-1 ring-line ${s}`}>
      {text}
    </span>
  )
}

// ---------- inputs ----------

const FIELD = 'w-full rounded-[10px] border border-line-strong bg-surface px-3 text-sm text-fg placeholder:text-fg-3 transition-colors hover:border-fg-3 focus-visible:border-fg focus-visible:outline-none'

export function TextInput({ className = '', ...props }: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`h-9 ${FIELD} ${className}`} />
}

export function TextArea({ className = '', ...props }: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={`py-2 leading-6 ${FIELD} ${className}`} />
}

export function SelectInput({ className = '', children, ...props }: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...props} className={`h-9 cursor-pointer pr-8 ${FIELD} ${className}`}>
      {children}
    </select>
  )
}

export function Field({ label, htmlFor, hint, children }: { label: string; htmlFor: string; hint?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-[13px] font-medium text-fg">
        {label}
      </label>
      {children}
      {hint && <p className="text-xs text-fg-3">{hint}</p>}
    </div>
  )
}

export function SearchInput({ value, onChange, placeholder = 'Search', label }: { value: string; onChange: (v: string) => void; placeholder?: string; label: string }) {
  return (
    <div className="relative">
      <Search aria-hidden="true" className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-fg-3" />
      <input
        type="search"
        aria-label={label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`h-9 w-64 max-w-full pl-9 ${FIELD}`}
      />
    </div>
  )
}

export function Segmented<T extends string>({
  value,
  onValueChange,
  options,
  label,
}: {
  value: T
  onValueChange: (val: T) => void
  options: { value: T; label: string; count?: number }[]
  label: string
}) {
  return (
    <SegmentedControl
      aria-label={label}
      value={value}
      onValueChange={(v) => onValueChange(v as T)}
      className="inline-flex h-9 items-center gap-0.5 rounded-[10px] bg-sunken p-0.5 ring-1 ring-line"
    >
      {options.map((opt) => (
        <SegmentedControlItem
          key={opt.value}
          value={opt.value}
          className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg px-3 text-[13px] font-medium whitespace-nowrap text-fg-2 transition-colors hover:text-fg data-checked:bg-surface data-checked:text-fg data-checked:shadow-[0_1px_2px_rgb(0_0_0/0.06)] data-checked:ring-1 data-checked:ring-line"
        >
          {opt.label}
          {opt.count !== undefined && opt.count > 0 && <span className="font-mono text-[11px] text-fg-3">{opt.count}</span>}
        </SegmentedControlItem>
      ))}
    </SegmentedControl>
  )
}

// ---------- overlays ----------

export function Modal({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  width = 'max-w-lg',
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: ReactNode
  children?: ReactNode
  footer?: ReactNode
  width?: string
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogBackdrop className="fixed inset-0 z-50 bg-black/40 transition-opacity duration-200 data-ending-style:opacity-0 data-starting-style:opacity-0" />
      <DialogContent
        className={`fixed top-1/2 left-1/2 z-50 w-[calc(100%-2rem)] ${width} -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-line bg-surface shadow-[0_24px_64px_rgb(0_0_0/0.18)] transition-[opacity,transform] duration-200 ease-out data-ending-style:opacity-0 data-starting-style:translate-y-[calc(-50%+8px)] data-starting-style:opacity-0`}
      >
        <div className="flex items-start justify-between gap-4 px-6 pt-5">
          <div className="min-w-0 space-y-1">
            <DialogTitle className="text-base font-medium text-fg">{title}</DialogTitle>
            {description && <DialogDescription className="text-sm leading-6 text-fg-2">{description}</DialogDescription>}
          </div>
          <IconButton label="Close" onClick={() => onOpenChange(false)} className="-mt-1 -mr-2">
            <X className={ICON} />
          </IconButton>
        </div>
        {children && <div className="space-y-4 px-6 pt-4">{children}</div>}
        <div className="flex justify-end gap-2 px-6 pt-5 pb-5">{footer}</div>
      </DialogContent>
    </Dialog>
  )
}

export function SidePanel({
  open,
  onOpenChange,
  eyebrow,
  title,
  meta,
  children,
  footer,
  width = 'max-w-xl',
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  eyebrow?: string
  title: ReactNode
  meta?: ReactNode
  children: ReactNode
  footer?: ReactNode
  width?: string
}) {
  return (
    <Drawer open={open} onOpenChange={onOpenChange} side="right">
      <DrawerBackdrop className="fixed inset-0 z-40 bg-black/30 transition-opacity duration-200 data-ending-style:opacity-0 data-starting-style:opacity-0" />
      <DrawerContent
        className={`fixed inset-y-0 right-0 z-40 flex w-full ${width} flex-col border-l border-line bg-surface shadow-[0_0_48px_rgb(0_0_0/0.16)]`}
      >
        <header className="flex items-start justify-between gap-4 border-b border-line px-6 py-4">
          <div className="min-w-0">
            {eyebrow && <p className="text-xs text-fg-3">{eyebrow}</p>}
            <DrawerTitle className="mt-0.5 truncate text-lg font-medium text-fg">{title}</DrawerTitle>
            {meta && <div className="mt-1 text-sm text-fg-2">{meta}</div>}
          </div>
          <IconButton label="Close panel" onClick={() => onOpenChange(false)} className="-mr-2">
            <X className={ICON} />
          </IconButton>
        </header>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <footer className="flex flex-wrap items-center justify-end gap-2 border-t border-line bg-raised px-6 py-3">{footer}</footer>}
      </DrawerContent>
    </Drawer>
  )
}

/**
 * Every consequential action goes through here: it says what will happen, who is affected,
 * whether it can be undone, and (when it matters) asks for the reason that goes in the activity log.
 */
export function ConfirmAction({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  tone = 'primary',
  reason,
  onConfirm,
  formatError,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: ReactNode
  confirmLabel: string
  tone?: 'primary' | 'danger'
  reason?: { label: string; placeholder?: string; required?: boolean; hint?: string }
  onConfirm: (reason: string) => Promise<void>
  formatError: (err: unknown) => string
}) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    if (open) {
      setText('')
      setError(null)
      setBusy(false)
    }
  }, [open])
  const blocked = reason?.required === true && text.trim().length < 3
  return (
    <Modal
      open={open}
      onOpenChange={(o) => !busy && onOpenChange(o)}
      title={title}
      description={description}
      footer={
        <>
          <Button variant="ghost" disabled={busy} onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            variant={tone === 'danger' ? 'danger-solid' : 'primary'}
            disabled={busy || blocked}
            onClick={async () => {
              setBusy(true)
              setError(null)
              try {
                await onConfirm(text.trim())
                onOpenChange(false)
              } catch (err) {
                setError(formatError(err))
              } finally {
                setBusy(false)
              }
            }}
          >
            {busy ? 'Working…' : confirmLabel}
          </Button>
        </>
      }
    >
      {reason && (
        <Field label={reason.label} htmlFor="confirm-reason" hint={reason.hint ?? (reason.required ? 'Required. Saved in the activity log.' : 'Saved in the activity log.')}>
          <TextArea id="confirm-reason" rows={3} maxLength={500} value={text} placeholder={reason.placeholder} onChange={(e) => setText(e.target.value)} />
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

// ---------- feedback ----------

export const notify = {
  done: (title: string, description?: string) => toast.success(title, { description }),
  failed: (title: string, description?: string) => toast.error(title, { description }),
}

export function Toasts() {
  return (
    <ToastRegion className="fixed right-4 bottom-4 z-[60] flex w-[360px] max-w-[calc(100vw-2rem)] flex-col gap-2">
      {() => (
        <Toast className="group flex items-start gap-3 rounded-xl border border-line bg-surface p-3.5 shadow-[0_12px_32px_rgb(0_0_0/0.14)] transition-[opacity,transform] duration-200 data-ending-style:opacity-0 data-starting-style:translate-y-2 data-starting-style:opacity-0">
          <CircleCheck aria-hidden="true" className="hidden size-4 shrink-0 translate-y-0.5 text-good group-data-[type=success]:block" />
          <CircleAlert aria-hidden="true" className="hidden size-4 shrink-0 translate-y-0.5 text-bad group-data-[type=error]:block" />
          <div className="min-w-0 flex-1">
            <ToastTitle className="text-sm font-medium text-fg" />
            <ToastDescription className="text-[13px] text-fg-2" />
          </div>
          <ToastClose aria-label="Dismiss" className="-m-1 inline-flex size-6 cursor-pointer items-center justify-center rounded-md text-fg-3 hover:bg-sunken hover:text-fg">
            <X className="size-3.5" />
          </ToastClose>
        </Toast>
      )}
    </ToastRegion>
  )
}

// ---------- theme ----------

type ThemeMode = 'light' | 'dark' | 'auto'

export function applyTheme(mode: ThemeMode) {
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const resolved = mode === 'auto' ? (prefersDark ? 'dark' : 'light') : mode
  document.documentElement.classList.remove('light', 'dark')
  document.documentElement.classList.add(resolved)
}

export function ThemeSwitch() {
  const [mode, setMode] = useState<ThemeMode>('auto')
  useEffect(() => {
    const stored = window.localStorage.getItem('theme')
    const initial: ThemeMode = stored === 'light' || stored === 'dark' || stored === 'auto' ? stored : 'auto'
    setMode(initial)
    applyTheme(initial)
  }, [])
  useEffect(() => {
    if (mode !== 'auto') return
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => applyTheme('auto')
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [mode])
  const options: { value: ThemeMode; label: string; icon: ReactNode }[] = [
    { value: 'light', label: 'Light theme', icon: <Sun className="size-3.5" /> },
    { value: 'dark', label: 'Dark theme', icon: <Moon className="size-3.5" /> },
    { value: 'auto', label: 'Match system', icon: <Monitor className="size-3.5" /> },
  ]
  return (
    <SegmentedControl
      aria-label="Theme"
      value={mode}
      onValueChange={(v) => {
        const next = v as ThemeMode
        setMode(next)
        applyTheme(next)
        window.localStorage.setItem('theme', next)
      }}
      className="inline-flex items-center gap-0.5 rounded-lg bg-sunken p-0.5 ring-1 ring-line"
    >
      {options.map((o) => (
        <SegmentedControlItem
          key={o.value}
          value={o.value}
          aria-label={o.label}
          title={o.label}
          className="inline-flex size-7 cursor-pointer items-center justify-center rounded-md text-fg-3 transition-colors hover:text-fg data-checked:bg-surface data-checked:text-fg data-checked:ring-1 data-checked:ring-line"
        >
          {o.icon}
        </SegmentedControlItem>
      ))}
    </SegmentedControl>
  )
}

// ---------- tables ----------

export const TABLE = 'w-full border-collapse text-left text-sm'
export const TH = 'h-10 border-b border-line px-4 text-xs font-medium whitespace-nowrap text-fg-3 first:pl-5 last:pr-5'
export const TD = 'h-14 border-b border-line px-4 align-middle first:pl-5 last:pr-5'
export const ROW = 'cursor-pointer transition-colors hover:bg-sunken focus-visible:bg-sunken focus-visible:outline-none [&:last-child>td]:border-b-0'

/** Row that opens on click and on Enter/Space, so keyboard users can reach every record. */
export function rowProps(onOpen: () => void, label: string) {
  return {
    tabIndex: 0,
    role: 'button' as const,
    'aria-label': label,
    onClick: onOpen,
    onKeyDown: (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        onOpen()
      }
    },
  }
}
