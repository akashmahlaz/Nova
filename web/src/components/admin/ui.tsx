import { useEffect, useState, type ReactNode } from 'react'
import { Button as SdkButton } from '@ui-sdk/react/button'
import {
  Dialog,
  DialogBackdrop,
  DialogContent,
  DialogTitle,
} from '@ui-sdk/react/dialog'
import {
  Drawer,
  DrawerBackdrop,
  DrawerContent,
  DrawerTitle,
} from '@ui-sdk/react/drawer'
import {
  SegmentedControl,
  SegmentedControlItem,
} from '@ui-sdk/react/segmented-control'
import { Monitor, Moon, Sun, X } from 'lucide-react'

export function NovaCard({
  children,
  className = '',
  gradient = false,
}: {
  children: ReactNode
  className?: string
  gradient?: boolean
}) {
  return (
    <div
      className={`rounded-2xl border border-zinc-200/80 dark:border-zinc-800 ${
        gradient
          ? 'bg-gradient-to-t from-zinc-950/[0.03] to-white dark:from-zinc-900 dark:to-zinc-950'
          : 'bg-white dark:bg-zinc-950'
      } shadow-xs ${className}`}
    >
      {children}
    </div>
  )
}

export function NovaBadge({
  children,
  tone = 'neutral',
  className = '',
}: {
  children: ReactNode
  tone?: 'neutral' | 'emerald' | 'amber' | 'crimson'
  className?: string
}) {
  const tones = {
    neutral:
      'border-zinc-200 bg-zinc-50 text-zinc-700 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300',
    emerald:
      'border-teal-500/25 bg-teal-500/10 text-teal-700 dark:border-teal-500/30 dark:bg-teal-500/15 dark:text-teal-300',
    amber:
      'border-amber-500/25 bg-amber-500/10 text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/15 dark:text-amber-300',
    crimson:
      'border-red-500/25 bg-red-500/10 text-red-600 dark:border-red-500/30 dark:bg-red-500/15 dark:text-red-400',
  }
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-medium ${tones[tone]} ${className}`}
    >
      {children}
    </span>
  )
}

export function NovaButton({
  children,
  variant = 'primary',
  size = 'md',
  className = '',
  ...props
}: React.ComponentProps<typeof SdkButton> & {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'emerald'
  size?: 'sm' | 'md'
}) {
  const variants = {
    primary:
      'bg-zinc-950 text-white hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-950 dark:hover:bg-zinc-200',
    secondary:
      'bg-zinc-100 text-zinc-900 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700',
    outline:
      'border border-zinc-200 bg-white text-zinc-900 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100 dark:hover:bg-zinc-900',
    ghost:
      'text-zinc-600 hover:bg-zinc-100 hover:text-zinc-950 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-50',
    danger:
      'border border-red-500/30 text-red-600 hover:bg-red-500/10 dark:border-red-500/30 dark:text-red-400 dark:hover:bg-red-500/15',
    emerald:
      'bg-teal-600 text-white hover:bg-teal-700 dark:bg-teal-600 dark:hover:bg-teal-500',
  }
  const sizes = {
    sm: 'h-7 px-2.5 text-[11px] rounded-md',
    md: 'h-9 px-3.5 text-xs rounded-lg',
  }
  return (
    <SdkButton
      {...props}
      className={`inline-flex items-center justify-center gap-1.5 font-medium transition-colors disabled:opacity-50 data-pending:opacity-60 cursor-pointer ${variants[variant]} ${sizes[size]} ${className}`}
    >
      {children}
    </SdkButton>
  )
}

export function NovaSegmented<T extends string>({
  value,
  onValueChange,
  options,
}: {
  value: T
  onValueChange: (val: T) => void
  options: { value: T; label: string }[]
}) {
  return (
    <SegmentedControl
      value={value}
      onValueChange={(v) => onValueChange(v as T)}
      className="inline-flex rounded-lg bg-zinc-100 dark:bg-zinc-900 p-0.5 border border-zinc-200/80 dark:border-zinc-800"
    >
      {options.map((opt) => (
        <SegmentedControlItem
          key={opt.value}
          value={opt.value}
          className="px-2.5 py-1 rounded-md text-xs font-medium capitalize text-zinc-600 dark:text-zinc-400 data-checked:bg-white dark:data-checked:bg-zinc-800 data-checked:text-zinc-950 dark:data-checked:text-zinc-50 data-checked:shadow-xs cursor-pointer transition-colors"
        >
          {opt.label}
        </SegmentedControlItem>
      ))}
    </SegmentedControl>
  )
}

export function NovaDialog({
  open,
  onOpenChange,
  title,
  subtitle,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  subtitle?: string
  children: ReactNode
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogBackdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs" />
      <DialogContent className="fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950 p-6 shadow-2xl space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <DialogTitle className="text-sm font-semibold text-zinc-950 dark:text-zinc-50">
              {title}
            </DialogTitle>
            {subtitle && (
              <p className="text-[11px] font-mono text-zinc-500 dark:text-zinc-400 mt-0.5">
                {subtitle}
              </p>
            )}
          </div>
          <NovaButton
            variant="ghost"
            size="sm"
            onClick={() => onOpenChange(false)}
            aria-label="Close dialog"
          >
            <X className="w-4 h-4" />
          </NovaButton>
        </div>
        {children}
      </DialogContent>
    </Dialog>
  )
}

export function NovaDrawer({
  open,
  onOpenChange,
  eyebrow,
  title,
  subtitle,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  eyebrow?: string
  title: string
  subtitle?: string
  children: ReactNode
}) {
  return (
    <Drawer open={open} onOpenChange={onOpenChange} side="right">
      <DrawerBackdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs" />
      <DrawerContent className="fixed inset-y-0 right-0 z-50 w-full max-w-md border-l border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950 p-6 shadow-2xl flex flex-col justify-between overflow-y-auto">
        <div className="space-y-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              {eyebrow && (
                <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400 dark:text-zinc-500">
                  {eyebrow}
                </div>
              )}
              <DrawerTitle className="text-base font-bold text-zinc-950 dark:text-zinc-50 mt-0.5">
                {title}
              </DrawerTitle>
              {subtitle && (
                <div className="font-mono text-[11px] text-zinc-500 dark:text-zinc-400">
                  {subtitle}
                </div>
              )}
            </div>
            <NovaButton
              variant="ghost"
              size="sm"
              onClick={() => onOpenChange(false)}
              aria-label="Close drawer"
            >
              <X className="w-4 h-4" />
            </NovaButton>
          </div>
          {children}
        </div>
      </DrawerContent>
    </Drawer>
  )
}

type ThemeMode = 'light' | 'dark' | 'auto'

function applyTheme(mode: ThemeMode) {
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const resolved = mode === 'auto' ? (prefersDark ? 'dark' : 'light') : mode
  document.documentElement.classList.remove('light', 'dark')
  document.documentElement.classList.add(resolved)
  document.documentElement.style.colorScheme = resolved
}

export function NovaThemeToggle() {
  const [mode, setMode] = useState<ThemeMode>('auto')

  useEffect(() => {
    const stored = window.localStorage.getItem('theme')
    const initial: ThemeMode =
      stored === 'light' || stored === 'dark' || stored === 'auto' ? stored : 'auto'
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

  function cycle() {
    const next: ThemeMode = mode === 'light' ? 'dark' : mode === 'dark' ? 'auto' : 'light'
    setMode(next)
    applyTheme(next)
    window.localStorage.setItem('theme', next)
  }

  return (
    <NovaButton
      variant="outline"
      size="sm"
      onClick={cycle}
      className="h-8 px-2.5 text-xs"
      title={`Theme: ${mode}`}
    >
      {mode === 'dark' ? (
        <Moon className="w-3.5 h-3.5" />
      ) : mode === 'light' ? (
        <Sun className="w-3.5 h-3.5" />
      ) : (
        <Monitor className="w-3.5 h-3.5" />
      )}
      <span className="capitalize">{mode}</span>
    </NovaButton>
  )
}
