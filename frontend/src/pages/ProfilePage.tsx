import { useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { Profile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Button, Card, ErrorText, Input, PageHeader } from '../components/ui'

const timezones: string[] = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ['UTC']

export function ProfilePage() {
  const { state, updateUser } = useAuth()
  const user = state.user!
  const [error, setError] = useState<unknown>(null)
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      const updated = await api.put<Profile>('/api/v1/me', {
        displayName: form.get('displayName'),
        timezone: form.get('timezone'),
        locale: form.get('locale'),
      })
      updateUser(updated)
      setSaved(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 md:px-8">
      <PageHeader title="Profile" description="Your timezone is used for dates in answers and, soon, reminders." />
      <Card className="p-6">
        <form onSubmit={onSubmit} className="space-y-5">
          <Input label="Email" value={user.email} disabled readOnly />
          <Input label="Display name" name="displayName" defaultValue={user.displayName} required maxLength={120} />
          <label className="block space-y-1.5">
            <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">Timezone</span>
            <select
              name="timezone"
              defaultValue={user.timezone}
              className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
            >
              {[...new Set([user.timezone, ...timezones])].map((tz) => (
                <option key={tz}>{tz}</option>
              ))}
            </select>
          </label>
          <Input label="Locale" name="locale" defaultValue={user.locale} required maxLength={20} />
          <ErrorText error={error} />
          <div className="flex items-center gap-3">
            <Button type="submit" disabled={busy}>
              {busy ? 'Saving…' : 'Save changes'}
            </Button>
            {saved && <span className="text-sm text-emerald-600">Saved</span>}
          </div>
        </form>
      </Card>
    </div>
  )
}
