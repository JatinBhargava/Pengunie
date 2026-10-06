import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from './client'

const session = (token: string) => ({
  accessToken: token,
  tokenType: 'Bearer' as const,
  expiresIn: 900,
  user: { id: 'u1', email: 'a@b.c', displayName: 'A', timezone: 'UTC', locale: 'en' },
})

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('ApiClient', () => {
  it('refreshes once on 401 and retries with the new token', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/auth/refresh') return json(200, session('fresh'))
      const auth = new Headers(init?.headers).get('Authorization')
      return auth === 'Bearer fresh' ? json(200, { ok: true }) : json(401, { code: 'expired' })
    })
    const client = new ApiClient(fetchMock as typeof fetch)
    client.setSession(session('stale'))

    await expect(client.get('/api/v1/me')).resolves.toEqual({ ok: true })
    expect(fetchMock.mock.calls.filter(([u]) => u === '/api/v1/auth/refresh')).toHaveLength(1)
  })

  it('shares a single refresh across concurrent 401s', async () => {
    let refreshCalls = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input) === '/api/v1/auth/refresh') {
        refreshCalls++
        await new Promise((r) => setTimeout(r, 10))
        return json(200, session('fresh'))
      }
      return new Headers(init?.headers).get('Authorization') === 'Bearer fresh' ? json(200, {}) : json(401, {})
    })
    const client = new ApiClient(fetchMock as typeof fetch)
    client.setSession(session('stale'))

    await Promise.all([client.get('/a'), client.get('/b'), client.get('/c')])
    expect(refreshCalls).toBe(1)
  })

  it('clears the session and surfaces the error when refresh fails', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) =>
      String(input) === '/api/v1/auth/refresh' ? json(401, {}) : json(401, { detail: 'nope', code: 'expired' }),
    )
    const client = new ApiClient(fetchMock as typeof fetch)
    const listener = vi.fn()
    client.onSessionChange(listener)
    client.setSession(session('stale'))

    const error = await client.get('/api/v1/me').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('expired')
    expect(listener).toHaveBeenLastCalledWith(null)
  })
})
