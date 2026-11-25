const API_BASE =
  (typeof import.meta !== 'undefined' &&
    (import.meta as unknown as { env?: Record<string, string> }).env?.VITE_API_BASE_URL) ||
  'https://nova-server-production.punjab.fireclaw.in'

export interface User {
  id: string
  phone_e164: string | null
  email: string | null
  first_name: string
  last_name: string
  role: 'rider' | 'driver' | 'admin'
  phone_verified_at: string | null
  email_verified_at: string | null
  created_at: string
}

export interface Session {
  access_token: string
  refresh_token: string
  expires_in: number
  user: User
}

export interface AdminStats {
  total_users: number
  verified_phones: number
  verified_emails: number
  saved_places: number
  emergency_contacts: number
}

const SESSION_KEY = 'nova_admin_session'

export function getStoredSession(): Session | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function saveStoredSession(session: Session | null) {
  if (typeof window === 'undefined') return
  if (session) {
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } else {
    window.localStorage.removeItem(SESSION_KEY)
  }
}

export class ApiError extends Error {
  constructor(public code: string) {
    super(code)
  }
}

async function requestJson<T>(
  path: string,
  options: RequestInit = {},
  auth = false,
): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const session = auth ? getStoredSession() : null
  if (auth && session?.access_token) {
    headers.set('Authorization', `Bearer ${session.access_token}`)
  }

  let res = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (res.status === 401 && auth && session?.refresh_token) {
    const refreshRes = await fetch(`${API_BASE}/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refresh_token: session.refresh_token }),
    })
    if (refreshRes.ok) {
      const next = (await refreshRes.json()) as Session
      saveStoredSession(next)
      headers.set('Authorization', `Bearer ${next.access_token}`)
      res = await fetch(`${API_BASE}${path}`, { ...options, headers })
    } else {
      saveStoredSession(null)
    }
  }

  const text = await res.text()
  const parsed = text ? JSON.parse(text) : {}
  if (!res.ok) {
    throw new ApiError(parsed.error || `http_${res.status}`)
  }
  return parsed as T
}

export const novaAdminApi = {
  sendOtp: (channel: 'email' | 'phone', destination: string) =>
    requestJson('/v1/auth/otp/send', {
      method: 'POST',
      body: JSON.stringify({ channel, destination, purpose: 'login' }),
    }),

  verifyOtp: (channel: 'email' | 'phone', destination: string, code: string) =>
    requestJson<Session>('/v1/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ channel, destination, purpose: 'login', code }),
    }),

  logout: async () => {
    const session = getStoredSession()
    if (session?.refresh_token) {
      await fetch(`${API_BASE}/v1/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refresh_token: session.refresh_token }),
      }).catch(() => {})
    }
    saveStoredSession(null)
  },

  getStats: () => requestJson<AdminStats>('/v1/admin/stats', {}, true),

  listUsers: () => requestJson<User[]>('/v1/admin/users', {}, true),

  updateRole: (userId: string, role: 'rider' | 'driver' | 'admin') =>
    requestJson<User>(
      `/v1/admin/users/${userId}/role`,
      { method: 'PATCH', body: JSON.stringify({ role }) },
      true,
    ),
}
