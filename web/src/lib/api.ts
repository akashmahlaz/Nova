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

/** An account as the console sees it: profile plus status and history counts. */
export interface AdminUser extends User {
  deactivated_at: string | null
  deactivated_reason: string | null
  rides_count: number
  has_driver_profile: boolean
}

export interface AuditEntry {
  id: string
  admin_id: string
  admin_name: string
  action: string
  target_type: 'user' | 'driver' | 'media' | 'service_area' | 'ride'
  target_id: string
  summary: string
  reason: string | null
  created_at: string
}

export interface AdminRideDetail {
  id: string
  status: string
  product_id: string
  service_area_name: string
  rider_id: string
  rider_name: string
  rider_phone: string | null
  passenger_name: string | null
  driver_id: string | null
  driver_name: string | null
  driver_phone: string | null
  vehicle: string | null
  pickup_name: string
  pickup_address: string
  dropoff_name: string
  dropoff_address: string
  currency: string
  fare_cents: number
  surge_multiplier: number
  distance_meters: number
  duration_seconds: number
  payment_method: string
  cancel_reason: string | null
  scheduled_at: string | null
  created_at: string
  accepted_at: string | null
  arrived_at: string | null
  started_at: string | null
  completed_at: string | null
  ended_at: string | null
  offers_sent: number
  offers_declined: number
}

export interface DailyRidePoint {
  /** YYYY-MM-DD */
  date: string
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
  /** Drivers with a live heartbeat in dispatch right now. */
  online_drivers: number
  /** Last 14 days, oldest first. */
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
  submitted_at: string | null
  docs_ready: number
  docs_required: number
  created_at: string
}

export interface AdminDriverDoc {
  category: string
  name: string
  description: string
  required: boolean
  media_id: string | null
  status: 'pending' | 'uploaded' | 'verified' | 'rejected' | null
  rejection_reason: string | null
  uploaded_at: string | null
  content_type: string | null
  view_url: string | null
}

export interface AdminDriverDetail {
  driver: {
    user_id: string
    first_name: string
    last_name: string
    phone_e164: string | null
    email: string | null
    status: 'pending' | 'approved' | 'suspended'
    licence_number: string
    licence_expiry: string | null
    submitted_at: string | null
    approved_at: string | null
    review_note: string | null
    created_at: string
  }
  vehicle: {
    make: string
    model: string
    color: string
    plate: string
    year: number
    seats: number
    is_electric: boolean
    wheelchair_accessible: boolean
  } | null
  products: string[]
  payout: {
    account_name: string
    account_number: string
    tax_number: string
    gst_registered: boolean
    payout_schedule: string
  } | null
  documents: AdminDriverDoc[]
  completed_trips: number
  ready_for_approval: boolean
}

export interface AdminRide {
  id: string
  rider_id: string
  driver_id: string | null
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
    | 'driver_endorsement'
    | 'driver_photo'
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

/**
 * One refresh at a time. Refresh tokens rotate and the server treats a reused one as stolen
 * (it revokes every session of the account), so parallel 401s must share a single refresh
 * instead of each spending the same token. Only a definite rejection signs the admin out;
 * a network blip keeps the stored session for the next try.
 */
let refreshing: Promise<Session | null> | null = null

function refreshSession(refreshToken: string): Promise<Session | null> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch(`${API_BASE}/v1/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify({ refresh_token: refreshToken }),
        })
        if (res.ok) {
          const next = (await res.json()) as Session
          saveStoredSession(next)
          return next
        }
        if (res.status === 401 || res.status === 403) saveStoredSession(null)
        return null
      } catch {
        return null
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
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
    const next = await refreshSession(session.refresh_token)
    if (next) {
      headers.set('Authorization', `Bearer ${next.access_token}`)
      res = await fetch(`${API_BASE}${path}`, { ...options, headers })
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

  listUsers: () => requestJson<AdminUser[]>('/v1/admin/users', {}, true),

  /** Signs the account out everywhere and blocks sign-in. History is kept. */
  deactivateUser: (userId: string, reason: string) =>
    requestJson<void>(`/v1/admin/users/${userId}/deactivate`, { method: 'POST', body: JSON.stringify({ reason }) }, true),

  reactivateUser: (userId: string, reason: string) =>
    requestJson<void>(`/v1/admin/users/${userId}/reactivate`, { method: 'POST', body: JSON.stringify({ reason }) }, true),

  listAudit: (params: { target_type?: AuditEntry['target_type']; target_id?: string; limit?: number } = {}) => {
    const q = new URLSearchParams()
    if (params.target_type) q.set('target_type', params.target_type)
    if (params.target_id) q.set('target_id', params.target_id)
    if (params.limit) q.set('limit', String(params.limit))
    const qs = q.toString()
    return requestJson<AuditEntry[]>(`/v1/admin/audit${qs ? `?${qs}` : ''}`, {}, true)
  },

  getRide: (id: string) => requestJson<AdminRideDetail>(`/v1/admin/rides/${id}`, {}, true),

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


  listDrivers: () => requestJson<AdminDriver[]>('/v1/admin/drivers', {}, true),

  getDriver: (userId: string) =>
    requestJson<AdminDriverDetail>(`/v1/admin/drivers/${userId}`, {}, true),

  /** `note` is shown to the driver when an application is sent back or an account is suspended. */
  updateDriverStatus: (
    userId: string,
    status: 'pending' | 'approved' | 'suspended',
    note?: string,
  ) =>
    requestJson<void>(
      `/v1/admin/drivers/${userId}`,
      { method: 'PATCH', body: JSON.stringify({ status, note: note?.trim() || null }) },
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
