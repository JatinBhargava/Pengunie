import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import type { DocumentStatus } from '../api/types'

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'ghost' | 'danger' }) {
  const styles = {
    primary: 'bg-accent-600 text-white hover:bg-accent-700 disabled:bg-accent-500/50',
    ghost: 'text-zinc-700 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800',
    danger: 'text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950',
  }[variant]
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed ${styles} ${className}`}
      {...props}
    />
  )
}

export function Input({ label, className = '', ...props }: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  const input = (
    <input
      className={`w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20 dark:border-zinc-700 dark:bg-zinc-900 ${className}`}
      {...props}
    />
  )
  if (!label) return input
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">{label}</span>
      {input}
    </label>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900 ${className}`}>
      {children}
    </div>
  )
}

export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Something went wrong'
  return <p className="text-sm text-red-600 dark:text-red-400">{message}</p>
}

const statusStyles: Record<DocumentStatus, string> = {
  UPLOADED: 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300',
  PARSING: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  EMBEDDING: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300',
  READY: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
  FAILED: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
}

export function StatusBadge({ status }: { status: DocumentStatus }) {
  const label = { UPLOADED: 'Queued', PARSING: 'Parsing', EMBEDDING: 'Indexing', READY: 'Ready', FAILED: 'Failed' }[status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ${statusStyles[status]}`}>
      {status !== 'READY' && status !== 'FAILED' && <span className="size-1.5 animate-pulse rounded-full bg-current" />}
      {label}
    </span>
  )
}

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{description}</p>}
      </div>
      {actions}
    </div>
  )
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function pageLabel(start: number, end: number) {
  return start === end ? `p. ${start}` : `pp. ${start}–${end}`
}
