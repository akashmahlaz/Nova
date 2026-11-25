const rawEnv =
  typeof import.meta !== 'undefined'
    ? (import.meta as unknown as { env?: Record<string, string> }).env
    : undefined

const API_BASE = (
  rawEnv?.SERVER_BASE_URL ||
  rawEnv?.VITE_API_BASE_URL ||
  'https://nova-server-production.punjab.fireclaw.in'
).replace(/\/+$/, '')

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

export interface DailyRidePoint {
  day: string
  total: number
  completed: number
  fare_cents: number
}

export interface AdminOverview {
  total_users: number
  rider_users: number
  driver_users: number
  admin_users: number
  verified_phones: number
  verified_emails: number
  total_drivers: number
  pending_drivers: number
  approved_drivers: number
  suspended_drivers: number
  total_rides: number
  active_rides: number
  completed_rides: number
  cancelled_rides: number
  scheduled_rides: number
  gross_fare_cents: number
  active_service_areas: number
  total_service_areas: number
  saved_places: number
  emergency_contacts: number
  media_total: number
  media_pending_review: number
  media_verified: number
  media_bytes: number
  s3_configured: boolean
  s3_bucket: string | null
  s3_region: string | null
  daily_rides: DailyRidePoint[]
}

export interface AdminDriver {
  user_id: string
  first_name: string
  last_name: string
  phone_e164: string | null
  status: 'pending' | 'approved' | 'suspended'
  licence_number: string
  licence_expiry: string | null
  vehicle: string | null
  created_at: string
}

export interface AdminRide {
  id: string
  status: string
  product_id: string
  service_area_name: string
  rider_name: string
  driver_name: string | null
  pickup_name: string
  dropoff_name: string
  currency: string
  fare_cents: number
  distance_meters: number
  duration_seconds: number
  payment_method: string
  created_at: string
}

export interface AdminServiceArea {
  id: string
  name: string
  region_code: string
  currency: string
  timezone: string
  active: boolean
  rate_count: number
  ride_count: number
  created_at: string
}

export interface AdminMediaItem {
  id: string
  owner_user_id: string
  owner_name: string
  owner_role: string
  bucket: string
  object_key: string
  category:
    | 'avatar'
    | 'driver_licence'
    | 'driver_insurance'
    | 'vehicle_reg'
    | 'support_attachment'
    | 'admin_asset'
  file_name: string
  content_type: string
  size_bytes: number
  status: 'pending' | 'uploaded' | 'verified' | 'rejected'
  rejection_reason: string | null
  view_url: string | null
  created_at: string
}

interface PresignUploadRes {
  media_id: string
  bucket: string
  object_key: string
  upload_url: string
  expires_in: number
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

  getOverview: () => requestJson<AdminOverview>('/v1/admin/overview', {}, true),

  listUsers: () => requestJson<User[]>('/v1/admin/users', {}, true),

  updateRole: (userId: string, role: 'rider' | 'driver' | 'admin') =>
    requestJson<User>(
      `/v1/admin/users/${userId}/role`,
      { method: 'PATCH', body: JSON.stringify({ role }) },
      true,
    ),

  createUser: (payload: {
    first_name: string
    last_name: string
    email?: string
    phone_e164?: string
    role: 'rider' | 'driver' | 'admin'
  }) =>
    requestJson<User>(
      '/v1/admin/users',
      { method: 'POST', body: JSON.stringify(payload) },
      true,
    ),

  updateUser: (
    userId: string,
    payload: {
      first_name?: string
      last_name?: string
      email?: string
      phone_e164?: string
      role?: 'rider' | 'driver' | 'admin'
      verify_email?: boolean
      verify_phone?: boolean
    },
  ) =>
    requestJson<User>(
      `/v1/admin/users/${userId}`,
      { method: 'PATCH', body: JSON.stringify(payload) },
      true,
    ),

  deleteUser: (userId: string) =>
    requestJson<void>(`/v1/admin/users/${userId}`, { method: 'DELETE' }, true),

  listDrivers: () => requestJson<AdminDriver[]>('/v1/admin/drivers', {}, true),

  updateDriverStatus: (userId: string, status: 'pending' | 'approved' | 'suspended') =>
    requestJson<void>(
      `/v1/admin/drivers/${userId}`,
      { method: 'PATCH', body: JSON.stringify({ status }) },
      true,
    ),

  listRides: () => requestJson<AdminRide[]>('/v1/admin/rides', {}, true),

  listServiceAreas: () => requestJson<AdminServiceArea[]>('/v1/admin/service-areas', {}, true),

  toggleServiceArea: (id: string, active: boolean) =>
    requestJson<void>(
      `/v1/admin/service-areas/${id}`,
      { method: 'PATCH', body: JSON.stringify({ active }) },
      true,
    ),

  listMedia: () => requestJson<AdminMediaItem[]>('/v1/admin/media', {}, true),

  reviewMedia: (id: string, status: 'verified' | 'rejected', rejection_reason?: string) =>
    requestJson<void>(
      `/v1/admin/media/${id}`,
      { method: 'PATCH', body: JSON.stringify({ status, rejection_reason: rejection_reason || null }) },
      true,
    ),

  deleteMedia: (id: string) =>
    requestJson<void>(`/v1/admin/media/${id}`, { method: 'DELETE' }, true),

  uploadToS3: async (file: File, category: AdminMediaItem['category'] = 'admin_asset') => {
    const presign = await requestJson<PresignUploadRes>(
      '/v1/media/presign-upload',
      {
        method: 'POST',
        body: JSON.stringify({
          category,
          content_type: file.type || 'image/png',
          file_name: file.name,
        }),
      },
      true,
    )
    const putRes = await fetch(presign.upload_url, {
      method: 'PUT',
      headers: { 'Content-Type': file.type || 'image/png' },
      body: file,
    })
    if (!putRes.ok) {
      throw new ApiError(`s3_upload_${putRes.status}`)
    }
    return requestJson(`/v1/media/${presign.media_id}/confirm`, { method: 'POST' }, true)
  },
}
