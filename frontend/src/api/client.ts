import type { Session } from './types'

/** Error carrying the RFC 9457 problem detail returned by the API. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

type SessionListener = (session: Session | null) => void

/**
 * Access token lives in memory only (never localStorage); the refresh token is an httpOnly cookie
 * the browser sends to /api/v1/auth/refresh. A 401 triggers one shared refresh, then a retry.
 */
export class ApiClient {
  private accessToken: string | null = null
  private refreshing: Promise<Session | null> | null = null
  private listeners = new Set<SessionListener>()
  private readonly fetchImpl: typeof fetch

  constructor(fetchImpl: typeof fetch = (...args) => fetch(...args)) {
    this.fetchImpl = fetchImpl
  }

  onSessionChange(listener: SessionListener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  setSession(session: Session | null) {
    this.accessToken = session?.accessToken ?? null
    this.listeners.forEach((l) => l(session))
  }

  /** Exchanges the refresh cookie for a new session. Concurrent callers share one request. */
  refresh(): Promise<Session | null> {
    if (!this.refreshing) {
      this.refreshing = this.fetchImpl('/api/v1/auth/refresh', { method: 'POST', credentials: 'same-origin' })
        .then(async (res) => (res.ok ? ((await res.json()) as Session) : null))
        .catch(() => null)
        .then((session) => {
          this.setSession(session)
          return session
        })
        .finally(() => {
          this.refreshing = null
        })
    }
    return this.refreshing
  }

  async request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const headers = new Headers(init.headers)
    if (this.accessToken) headers.set('Authorization', `Bearer ${this.accessToken}`)
    if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    const res = await this.fetchImpl(path, { ...init, headers, credentials: 'same-origin' })

    if (res.status === 401 && retry && !path.startsWith('/api/v1/auth/')) {
      const session = await this.refresh()
      if (session) return this.request<T>(path, init, false)
    }
    if (!res.ok) throw await toApiError(res)
    if (res.status === 204) return undefined as T
    const text = await res.text()
    return (text ? JSON.parse(text) : undefined) as T
  }

  get<T>(path: string) {
    return this.request<T>(path)
  }

  post<T>(path: string, body?: unknown) {
    return this.request<T>(path, {
      method: 'POST',
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
    })
  }

  put<T>(path: string, body: unknown) {
    return this.request<T>(path, { method: 'PUT', body: JSON.stringify(body) })
  }

  patch<T>(path: string, body: unknown) {
    return this.request<T>(path, { method: 'PATCH', body: JSON.stringify(body) })
  }

  delete(path: string) {
    return this.request<void>(path, { method: 'DELETE' })
  }
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const problem = (await res.json()) as { detail?: string; title?: string; code?: string }
    return new ApiError(res.status, problem.detail ?? problem.title ?? res.statusText, problem.code)
  } catch {
    return new ApiError(res.status, res.statusText || `Request failed (${res.status})`)
  }
}

export const api = new ApiClient()
