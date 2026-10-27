<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type Leaderboard } from '../api'
import AutoScroll from '../components/AutoScroll.vue'
import JoinQr from '../components/JoinQr.vue'
import ScoreRow from '../components/ScoreRow.vue'

const POLL_MS = 10_000
const EVEREST_METERS = 8849

const board = ref<Leaderboard | null>(null)
const stale = ref(false)
let timer: ReturnType<typeof setInterval> | undefined

async function refresh() {
  try {
    board.value = await api.leaderboard()
    stale.value = false
  } catch {
    // Keep showing the last known board; a flaky bar wifi shouldn't blank the screen.
    stale.value = true
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, POLL_MS)
})
onUnmounted(() => clearInterval(timer))

const everests = computed(() => ((board.value?.grandTotal ?? 0) / EVEREST_METERS).toFixed(1))
const fmt = (n: number) => n.toLocaleString('nl-BE')

// Tied climbers share a row, so these are the top three places.
const PINNED = 3
const podium = computed(() => board.value?.rows.slice(0, PINNED) ?? [])
const rest = computed(() => board.value?.rows.slice(PINNED) ?? [])
</script>

<template>
  <main class="[--u:min(1vh,0.5625vw)] flex h-dvh flex-col gap-[calc(3*var(--u))] overflow-hidden bg-night-900 p-[calc(4*var(--u))] text-ember-50">
    <header class="flex items-end justify-between">
      <h1 class="text-[calc(6*var(--u))] leading-none font-black tracking-tight">
        Samen naar de <span class="text-ember-500">Everest</span>
      </h1>
      <p class="text-[calc(2.2*var(--u))] text-ember-50/60">
        voor De Warmste Week
        <span v-if="stale" class="ml-3 text-red-400">● offline</span>
      </p>
    </header>

    <section
      class="flex items-center justify-between gap-[calc(4*var(--u))] rounded-3xl bg-gradient-to-r from-ember-600 to-ember-400 px-[calc(4*var(--u))] py-[calc(3*var(--u))] text-night-900"
    >
      <div>
        <p class="text-[calc(2.4*var(--u))] font-semibold uppercase tracking-widest">Samen geklommen</p>
        <p class="text-[calc(12*var(--u))] leading-none font-black tabular-nums">
          {{ fmt(board?.grandTotal ?? 0) }}<span class="text-[calc(5*var(--u))]"> m</span>
        </p>
        <p class="mt-[calc(1*var(--u))] text-[calc(2.4*var(--u))] font-semibold">
          {{ board?.climberCount ?? 0 }} klimmers · {{ everests }}× de Mount Everest
        </p>
      </div>
      <JoinQr />
    </section>

    <p v-if="board && board.rows.length === 0" class="m-auto text-[calc(4*var(--u))] text-ember-50/60">
      Nog niemand geklommen. Wie zet de eerste meters?
    </p>
    <template v-else>
      <ol class="flex flex-col gap-[calc(1.2*var(--u))]">
        <ScoreRow v-for="row in podium" :key="row.rank" :row="row" />
      </ol>
      <AutoScroll v-if="rest.length" class="border-t-2 border-night-700">
        <ol class="flex flex-col gap-[calc(0.8*var(--u))]">
          <ScoreRow v-for="row in rest" :key="row.rank" :row="row" compact />
        </ol>
      </AutoScroll>
    </template>
  </main>
</template>
