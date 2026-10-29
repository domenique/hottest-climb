<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi, AdminError, isLoggedIn, login, logout, type AdminEntry, type ClimberSummary } from '../adminApi'

const EUROS_PER_ENTRY = 5

type Tab = 'entries' | 'climbers'

const loggedIn = ref(isLoggedIn())
const password = ref('')
const tab = ref<Tab>('entries')
const entries = ref<AdminEntry[]>([])
const climbers = ref<ClimberSummary[]>([])
const search = ref('')
const onlyUnpaid = ref(true)
const error = ref('')
const busy = ref(false)

// Per-row UI state, keyed by entry or climber id.
const meterDrafts = ref<Record<string, number>>({})
const pinDrafts = ref<Record<string, string>>({})
const confirming = ref<string | null>(null)

function fail(e: unknown) {
  error.value = e instanceof AdminError ? e.message : 'Geen verbinding. Probeer opnieuw.'
  loggedIn.value = isLoggedIn()
}

async function run(action: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await reload()
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

async function reload() {
  const [e, c] = await Promise.all([adminApi.entries(), adminApi.climbers()])
  entries.value = e
  climbers.value = c.sort((a, b) => a.name.localeCompare(b.name, 'nl'))
  meterDrafts.value = {}
  pinDrafts.value = {}
  confirming.value = null
}

async function submitLogin() {
  busy.value = true
  error.value = ''
  try {
    await login(password.value)
    password.value = ''
    loggedIn.value = true
    await reload()
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

function signOut() {
  logout()
  loggedIn.value = false
}

onMounted(() => {
  if (loggedIn.value) run(async () => {})
})

const matches = (name: string) => name.toLowerCase().includes(search.value.trim().toLowerCase())
const visibleEntries = computed(() =>
  entries.value.filter((e) => matches(e.climberName) && (!onlyUnpaid.value || !e.paid)),
)
const visibleClimbers = computed(() => climbers.value.filter((c) => matches(c.name)))
const unpaidCount = computed(() => entries.value.filter((e) => !e.paid).length)

const when = (iso: string) =>
  new Date(iso).toLocaleString('nl-BE', {
    timeZone: 'Europe/Brussels',
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
const euros = (n: number) => n.toLocaleString('nl-BE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

const saveMeters = (entry: AdminEntry) => run(() => adminApi.changeEntry(entry.id, { meters: meterDrafts.value[entry.id] }))
const togglePaid = (entry: AdminEntry) => run(() => adminApi.changeEntry(entry.id, { paid: !entry.paid }))
const deleteEntry = (entry: AdminEntry) => run(() => adminApi.deleteEntry(entry.id))
const resetPin = (climber: ClimberSummary) => run(() => adminApi.resetPin(climber.id, pinDrafts.value[climber.id] ?? ''))
const deleteClimber = (climber: ClimberSummary) => run(() => adminApi.deleteClimber(climber.id))

const meterChanged = (entry: AdminEntry) =>
  meterDrafts.value[entry.id] !== undefined && meterDrafts.value[entry.id] !== entry.meters

const button = 'rounded-lg px-3 py-1.5 text-sm font-semibold transition disabled:opacity-50'
const input = 'rounded-lg border border-night-700 bg-night-900 px-3 py-1.5 outline-none focus:border-ember-500'
</script>

<template>
  <main class="min-h-dvh bg-night-900 px-4 py-8 text-ember-50">
    <div class="mx-auto max-w-4xl">
      <header class="flex flex-wrap items-center justify-between gap-4">
        <h1 class="text-3xl font-black tracking-tight">
          Admin
        </h1>
        <button v-if="loggedIn" type="button" :class="[button, 'bg-night-800 text-ember-50/80']" @click="signOut">
          Afmelden
        </button>
      </header>

      <p v-if="error" role="alert" class="mt-6 rounded-lg bg-red-500/15 px-4 py-3 text-red-300">{{ error }}</p>

      <form v-if="!loggedIn" class="mt-8 max-w-sm space-y-4" @submit.prevent="submitLogin">
        <label class="block">
          <span class="text-sm text-ember-50/70">Admin-wachtwoord</span>
          <input
            v-model="password"
            required
            type="password"
            autocomplete="current-password"
            class="mt-1 w-full rounded-lg border border-night-700 bg-night-800 px-4 py-3 text-lg outline-none focus:border-ember-500"
          />
        </label>
        <button
          type="submit"
          :disabled="busy"
          class="w-full rounded-xl bg-ember-500 py-3 font-bold text-night-900 hover:bg-ember-400 disabled:opacity-50"
        >
          Aanmelden
        </button>
      </form>

      <template v-else>
        <section class="mt-6 grid grid-cols-3 gap-3 text-center">
          <div class="rounded-xl bg-night-800 p-4">
            <p class="text-2xl font-black tabular-nums">{{ entries.length }}</p>
            <p class="text-xs text-ember-50/60 uppercase">ingaves</p>
          </div>
          <div class="rounded-xl bg-night-800 p-4">
            <p class="text-2xl font-black tabular-nums text-amber-300">{{ unpaidCount }}</p>
            <p class="text-xs text-ember-50/60 uppercase">onbetaald</p>
          </div>
          <div class="rounded-xl bg-night-800 p-4">
            <p class="text-2xl font-black tabular-nums text-amber-300">{{ euros(unpaidCount * EUROS_PER_ENTRY) }}</p>
            <p class="text-xs text-ember-50/60 uppercase">nog te innen</p>
          </div>
        </section>

        <div class="mt-6 flex flex-wrap items-center gap-3">
          <div class="grid grid-cols-2 rounded-xl bg-night-800 p-1 text-sm font-semibold">
            <button
              v-for="option in (['entries', 'climbers'] as const)"
              :key="option"
              type="button"
              class="rounded-lg px-4 py-1.5 transition"
              :class="tab === option ? 'bg-ember-500 text-night-900' : 'text-ember-50/70'"
              @click="tab = option"
            >
              {{ option === 'entries' ? 'Ingaves' : 'Klimmers' }}
            </button>
          </div>
          <input v-model="search" type="search" placeholder="Zoek op naam" :class="[input, 'min-w-0 flex-1']" />
          <label v-if="tab === 'entries'" class="flex items-center gap-2 text-sm">
            <input v-model="onlyUnpaid" type="checkbox" class="accent-ember-500" />
            Alleen onbetaald
          </label>
        </div>

        <ul v-if="tab === 'entries'" class="mt-4 space-y-2">
          <li
            v-for="entry in visibleEntries"
            :key="entry.id"
            class="flex flex-wrap items-center gap-3 rounded-xl bg-night-800 px-4 py-3"
          >
            <div class="min-w-0 flex-1">
              <p class="truncate font-bold">{{ entry.climberName }}</p>
              <p class="text-xs text-ember-50/60">{{ when(entry.loggedAt) }}</p>
            </div>

            <form class="flex items-center gap-2" @submit.prevent="saveMeters(entry)">
              <input
                :value="meterDrafts[entry.id] ?? entry.meters"
                type="number"
                min="1"
                max="500"
                required
                aria-label="Meters"
                :class="[input, 'w-24 text-right tabular-nums']"
                @input="meterDrafts[entry.id] = Number(($event.target as HTMLInputElement).value)"
              />
              <span class="text-sm text-ember-50/60">m</span>
              <button v-if="meterChanged(entry)" type="submit" :disabled="busy" :class="[button, 'bg-ember-500 text-night-900']">
                Opslaan
              </button>
            </form>

            <button
              type="button"
              :disabled="busy"
              :class="[button, entry.paid ? 'bg-emerald-500/20 text-emerald-300' : 'bg-amber-400/20 text-amber-300']"
              :aria-pressed="entry.paid"
              @click="togglePaid(entry)"
            >
              {{ entry.paid ? '✓ Betaald' : '€ Onbetaald' }}
            </button>

            <span v-if="confirming === entry.id" class="flex items-center gap-2 text-sm">
              Verwijderen?
              <button type="button" :disabled="busy" :class="[button, 'bg-red-500 text-white']" @click="deleteEntry(entry)">
                Ja
              </button>
              <button type="button" :class="[button, 'bg-night-700']" @click="confirming = null">Nee</button>
            </span>
            <button v-else type="button" :class="[button, 'bg-night-700 text-red-300']" @click="confirming = entry.id">
              Verwijderen
            </button>
          </li>
          <li v-if="visibleEntries.length === 0" class="py-8 text-center text-ember-50/60">
            {{ onlyUnpaid && !search ? 'Alles is betaald.' : 'Geen ingaves.' }}
          </li>
        </ul>

        <ul v-else class="mt-4 space-y-2">
          <li
            v-for="climber in visibleClimbers"
            :key="climber.id"
            class="flex flex-wrap items-center gap-3 rounded-xl bg-night-800 px-4 py-3"
          >
            <div class="min-w-0 flex-1">
              <p class="truncate font-bold">{{ climber.name }}</p>
              <p class="text-xs text-ember-50/60">{{ climber.entryCount }} ingaves · {{ climber.meters }} m</p>
            </div>

            <form
              v-if="pinDrafts[climber.id] !== undefined"
              class="flex items-center gap-2"
              @submit.prevent="resetPin(climber)"
            >
              <input
                v-model="pinDrafts[climber.id]"
                required
                inputmode="numeric"
                pattern="\d{4,6}"
                placeholder="Nieuwe pin"
                aria-label="Nieuwe pincode"
                :class="[input, 'w-32 tracking-widest']"
              />
              <button type="submit" :disabled="busy" :class="[button, 'bg-ember-500 text-night-900']">Opslaan</button>
              <button type="button" :class="[button, 'bg-night-700']" @click="delete pinDrafts[climber.id]">Annuleer</button>
            </form>
            <button v-else type="button" :class="[button, 'bg-night-700']" @click="pinDrafts[climber.id] = ''">
              Pin resetten
            </button>

            <span v-if="confirming === climber.id" class="flex flex-wrap items-center gap-2 text-sm">
              Verwijdert ook {{ climber.entryCount }} ingaves.
              <button type="button" :disabled="busy" :class="[button, 'bg-red-500 text-white']" @click="deleteClimber(climber)">
                Ja, verwijder
              </button>
              <button type="button" :class="[button, 'bg-night-700']" @click="confirming = null">Nee</button>
            </span>
            <button v-else type="button" :class="[button, 'bg-night-700 text-red-300']" @click="confirming = climber.id">
              Verwijderen
            </button>
          </li>
          <li v-if="visibleClimbers.length === 0" class="py-8 text-center text-ember-50/60">Geen klimmers.</li>
        </ul>
      </template>
    </div>
  </main>
</template>
