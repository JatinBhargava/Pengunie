import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorText, Input } from '../components/ui'

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <img src="/favicon.svg" alt="" className="mb-6 size-10" />
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-1 mb-8 text-sm text-zinc-500 dark:text-zinc-400">{subtitle}</p>
        {children}
      </div>
    </div>
  )
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setBusy(true)
    setError(null)
    try {
      await login(String(form.get('email')), String(form.get('password')))
      navigate('/chat')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to your Personal Intelligence OS.">
      <form onSubmit={onSubmit} className="space-y-4">
        <Input label="Email" name="email" type="email" autoComplete="email" required />
        <Input label="Password" name="password" type="password" autoComplete="current-password" required />
        <ErrorText error={error} />
        <Button type="submit" disabled={busy} className="w-full">
          {busy ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
      <p className="mt-6 text-sm text-zinc-500">
        New here?{' '}
        <Link to="/register" className="font-medium text-accent-600 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthShell>
  )
}

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setBusy(true)
    setError(null)
    try {
      await register({
        email: String(form.get('email')),
        password: String(form.get('password')),
        displayName: String(form.get('displayName')),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      })
      navigate('/documents')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your space" subtitle="Your documents stay private to you.">
      <form onSubmit={onSubmit} className="space-y-4">
        <Input label="Name" name="displayName" autoComplete="name" required maxLength={120} />
        <Input label="Email" name="email" type="email" autoComplete="email" required />
        <Input label="Password" name="password" type="password" autoComplete="new-password" required minLength={10} />
        <p className="text-xs text-zinc-500">At least 10 characters.</p>
        <ErrorText error={error} />
        <Button type="submit" disabled={busy} className="w-full">
          {busy ? 'Creating…' : 'Create account'}
        </Button>
      </form>
      <p className="mt-6 text-sm text-zinc-500">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-accent-600 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}
