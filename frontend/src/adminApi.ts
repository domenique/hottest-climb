export interface AdminEntry {
  id: string
  climberId: string
  climberName: string
  meters: number
  loggedAt: string
  paid: boolean
}

export interface ClimberSummary {
  id: string
  name: string
  entryCount: number
  meters: number
}

export class AdminError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

const TOKEN_KEY = 'hottest-climb:admin'

// Session storage: the password is forgotten when the tab closes.
function load(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

let token: string | null = load()

// The backend expects base64(UTF-8 password), so passwords with spaces or symbols still form a valid bearer token.
function encode(password: string): string {
  return btoa(String.fromCharCode(...new TextEncoder().encode(password)))
}

const messages: Record<number, string> = {
  400: 'Ongeldige invoer: 1–500 meter, pincode 4–6 cijfers.',
  401: 'Verkeerd wachtwoord.',
  404: 'Niet gevonden. Misschien al verwijderd? Of de admin is niet ingeschakeld (ADMIN_PASSWORD).',
  429: 'Te veel verzoeken. Wacht even.',
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/admin${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token ?? ''}` },
  })
  if (!response.ok) {
    if (response.status === 401) logout()
    throw new AdminError(response.status, messages[response.status] ?? 'Er ging iets mis. Probeer opnieuw.')
  }
  return (response.status === 204 ? undefined : await response.json()) as T
}

export function isLoggedIn() {
  return token !== null
}

export async function login(password: string) {
  token = encode(password)
  try {
    await request('/entries')
  } catch (e) {
    logout()
    throw e
  }
  try {
    sessionStorage.setItem(TOKEN_KEY, token)
  } catch {
    // Private browsing: stays logged in until reload.
  }
}

export function logout() {
  token = null
  try {
    sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // nothing stored
  }
}

const json = (body: unknown) => JSON.stringify(body)

export const adminApi = {
  entries: () => request<AdminEntry[]>('/entries'),
  changeEntry: (id: string, change: { meters?: number; paid?: boolean }) =>
    request<void>(`/entries/${id}`, { method: 'PATCH', body: json(change) }),
  deleteEntry: (id: string) => request<void>(`/entries/${id}`, { method: 'DELETE' }),
  climbers: () => request<ClimberSummary[]>('/climbers'),
  resetPin: (id: string, pin: string) => request<void>(`/climbers/${id}/pin`, { method: 'PUT', body: json({ pin }) }),
  deleteClimber: (id: string) => request<void>(`/climbers/${id}`, { method: 'DELETE' }),
}
