import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { Profile, Session } from '../api/types'

type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'anonymous'; user: null }
  | { status: 'authenticated'; user: Profile }

interface AuthContextValue {
  state: AuthState
  login: (email: string, password: string) => Promise<void>
  register: (input: { email: string; password: string; displayName: string; timezone: string }) => Promise<void>
  logout: () => Promise<void>
  updateUser: (user: Profile) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })

  useEffect(() => {
    const unsubscribe = api.onSessionChange((session) =>
      setState(session ? { status: 'authenticated', user: session.user } : { status: 'anonymous', user: null }),
    )
    // Restore the session from the refresh cookie on page load.
    void api.refresh()
    return unsubscribe
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    api.setSession(await api.post<Session>('/api/v1/auth/login', { email, password }))
  }, [])

  const register = useCallback(
    async (input: { email: string; password: string; displayName: string; timezone: string }) => {
      api.setSession(await api.post<Session>('/api/v1/auth/register', input))
    },
    [],
  )

  const logout = useCallback(async () => {
    try {
      await api.post('/api/v1/auth/logout')
    } finally {
      api.setSession(null)
    }
  }, [])

  const updateUser = useCallback((user: Profile) => setState({ status: 'authenticated', user }), [])

  const value = useMemo(
    () => ({ state, login, register, logout, updateUser }),
    [state, login, register, logout, updateUser],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
