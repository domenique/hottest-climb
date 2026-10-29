export interface LeaderboardRow {
  rank: number
  names: string[]
  meters: number
}

export interface Leaderboard {
  rows: LeaderboardRow[]
  grandTotal: number
  climberCount: number
  raisedEuros: number
  pendingEuros: number
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

const messages: Record<number, string> = {
  400: 'Controleer je invoer: naam 2–30 tekens, pincode 4–6 cijfers, 1–500 meter.',
  401: 'Onbekende naam of verkeerde pincode.',
  409: 'Die naam is al in gebruik. Kies een andere.',
  429: 'Te veel pogingen. Probeer het over een paar minuten opnieuw.',
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json' },
  })
  if (!response.ok) {
    throw new ApiError(response.status, messages[response.status] ?? 'Er ging iets mis. Probeer opnieuw.')
  }
  return response.json() as Promise<T>
}

export const api = {
  register: (name: string, pin: string) =>
    request<{ name: string }>('/api/climbers', { method: 'POST', body: JSON.stringify({ name, pin }) }),

  logClimb: (name: string, pin: string, meters: number) =>
    request<{ name: string; total: number }>('/api/climbs', {
      method: 'POST',
      body: JSON.stringify({ name, pin, meters }),
    }),

  leaderboard: () => request<Leaderboard>('/api/leaderboard'),
}
