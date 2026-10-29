<script setup lang="ts">
import { ref } from 'vue'
import { api, ApiError } from '../api'

type Mode = 'log' | 'register'

const NAME_KEY = 'hottest-climb:name'

function storedName(): string {
  try {
    return localStorage.getItem(NAME_KEY) ?? ''
  } catch {
    return ''
  }
}

const mode = ref<Mode>(storedName() ? 'log' : 'register')
const name = ref(storedName())
const pin = ref('')
const meters = ref<number | null>(null)
const busy = ref(false)
const error = ref('')
const success = ref('')

function remember(value: string) {
  try {
    localStorage.setItem(NAME_KEY, value)
  } catch {
    // Private browsing: the name just won't be prefilled next time.
  }
}

function switchMode(next: Mode) {
  mode.value = next
  error.value = ''
  success.value = ''
}

async function submit() {
  busy.value = true
  error.value = ''
  success.value = ''
  try {
    if (mode.value === 'register') {
      const climber = await api.register(name.value, pin.value)
      name.value = climber.name
      remember(climber.name)
      mode.value = 'log'
      success.value = `Welkom ${climber.name}! Vul nu je eerste meters in.`
    } else {
      const result = await api.logClimb(name.value, pin.value, meters.value ?? 0)
      remember(result.name)
      meters.value = null
      success.value = `Top! Je staat nu op ${result.total.toLocaleString('nl-BE')} meter. Vergeet je € 5 niet te betalen aan de bar.`
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Geen verbinding. Probeer opnieuw.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="min-h-dvh bg-night-900 px-4 py-10 text-ember-50">
    <div class="mx-auto max-w-md">
      <h1 class="text-4xl font-black tracking-tight">
        Klimmen voor <span class="text-ember-500">de warmste week</span>
      </h1>
      <p class="mt-2 text-ember-50/70">Elke meter telt! Laten we samen top van de K2 bereiken!</p>

      <div class="mt-8 grid grid-cols-2 rounded-xl bg-night-800 p-1 text-sm font-semibold">
        <button
          v-for="option in (['log', 'register'] as const)"
          :key="option"
          type="button"
          class="rounded-lg py-2 transition"
          :class="mode === option ? 'bg-ember-500 text-night-900' : 'text-ember-50/70'"
          @click="switchMode(option)"
        >
          {{ option === 'log' ? 'Meters invullen' : 'Nieuwe klimmer' }}
        </button>
      </div>

      <form class="mt-6 space-y-4" @submit.prevent="submit">
        <label class="block">
          <span class="text-sm text-ember-50/70">Naam</span>
          <input
            v-model="name"
            required
            minlength="2"
            maxlength="30"
            autocomplete="username"
            class="mt-1 w-full rounded-lg border border-night-700 bg-night-800 px-4 py-3 text-lg outline-none focus:border-ember-500"
          />
        </label>

        <label class="block">
          <span class="text-sm text-ember-50/70">
            Pincode {{ mode === 'register' ? '(kies 4–6 cijfers, onthoud ze goed)' : '' }}
          </span>
          <input
            v-model="pin"
            required
            type="password"
            inputmode="numeric"
            pattern="\d{4,6}"
            :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
            class="mt-1 w-full rounded-lg border border-night-700 bg-night-800 px-4 py-3 text-lg tracking-widest outline-none focus:border-ember-500"
          />
        </label>

        <label v-if="mode === 'log'" class="block">
          <span class="text-sm text-ember-50/70">Meters geklommen</span>
          <input
            v-model.number="meters"
            required
            type="number"
            min="1"
            max="500"
            inputmode="numeric"
            class="mt-1 w-full rounded-lg border border-night-700 bg-night-800 px-4 py-3 text-3xl font-bold outline-none focus:border-ember-500"
          />
        </label>

        <p
          v-if="mode === 'log'"
          role="note"
          class="flex gap-3 rounded-lg border border-amber-400/40 bg-amber-400/10 px-4 py-3 text-sm text-amber-200"
        >
          <span aria-hidden="true">⚠️</span>
          <span>
            Elke ingave kost <strong>€ 5</strong>, die gaan naar De Warmste Week.
            Betaal je ingave aan de <strong>bar</strong>.
          </span>
        </p>

        <button
          type="submit"
          :disabled="busy"
          class="w-full rounded-xl bg-ember-500 py-4 text-lg font-bold text-night-900 transition hover:bg-ember-400 disabled:opacity-50"
        >
          {{ mode === 'log' ? 'Meters toevoegen' : 'Registreren' }}
        </button>

        <p v-if="error" role="alert" class="rounded-lg bg-red-500/15 px-4 py-3 text-red-300">{{ error }}</p>
        <p v-if="success" role="status" class="rounded-lg bg-ember-500/15 px-4 py-3 text-ember-400">{{ success }}</p>
      </form>
    </div>
  </main>
</template>
