import { ApiError } from './api'

const MESSAGES: Record<string, string> = {
  account_not_found: 'No Nova account uses that email or phone.',
  invalid_code: "That code isn't right. Check the latest message and try again.",
  code_not_found: 'That code has expired. Send a new one.',
  too_many_requests: 'Too many attempts. Wait a moment and try again.',
  unauthorized: "Your session ended or this account isn't an admin. Sign in again.",
  account_deactivated: 'This account is deactivated.',
  s3_unavailable: "File storage isn't configured on the server.",
  user_already_exists: 'An account with that email or phone already exists.',
  contact_already_in_use: 'Another account already uses that email or phone.',
  documents_missing: "Can't approve yet: a required document or the car is missing.",
  reason_required: 'Add a short reason. It goes in the activity log.',
  cannot_change_own_role: "You can't change your own role. Ask another admin.",
  cannot_deactivate_self: "You can't deactivate your own account.",
  last_admin: "This is the last active admin. Make someone else an admin first.",
  driver_profile_exists: 'This account has a driver application, so it stays a driver.',
  record_kept: 'Driver documents and profile photos are kept as records. Send it back instead.',
  user_not_found: 'That account no longer exists.',
  driver_not_found: 'That driver application no longer exists.',
  media_not_found: 'That file no longer exists.',
  ride_not_found: 'That trip no longer exists.',
  invalid_role: 'Choose rider, driver or admin.',
  contact_required: 'Add an email or a phone number.',
  invalid_name: 'Add a first name.',
}

export function formatError(err: unknown): string {
  if (err instanceof ApiError) return MESSAGES[err.code] ?? `Something went wrong (${err.code}).`
  return "Couldn't reach the server. Check your connection and try again."
}
