<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type Leaderboard } from '../api'

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
const medal = (rank: number) => ['🥇', '🥈', '🥉'][rank - 1] ?? rank
</script>

<template>
  <main class="flex h-dvh flex-col gap-[3vh] overflow-hidden bg-night-900 p-[4vh] text-ember-50">
    <header class="flex items-end justify-between">
      <h1 class="text-[6vh] leading-none font-black tracking-tight">
        Hottest <span class="text-ember-500">Climb</span>
      </h1>
      <p class="text-[2.2vh] text-ember-50/60">
        voor De Warmste Week
        <span v-if="stale" class="ml-3 text-red-400">● offline</span>
      </p>
    </header>

    <section class="rounded-3xl bg-gradient-to-r from-ember-600 to-ember-400 px-[4vh] py-[3vh] text-night-900">
      <p class="text-[2.4vh] font-semibold uppercase tracking-widest">Samen geklommen</p>
      <p class="text-[12vh] leading-none font-black tabular-nums">
        {{ fmt(board?.grandTotal ?? 0) }}<span class="text-[5vh]"> m</span>
      </p>
      <p class="mt-[1vh] text-[2.4vh] font-semibold">
        {{ board?.climberCount ?? 0 }} klimmers · {{ everests }}× de Mount Everest
      </p>
    </section>

    <ol class="flex min-h-0 flex-1 flex-col gap-[1.2vh]">
      <li
        v-for="row in board?.rows ?? []"
        :key="row.name"
        class="flex flex-1 items-center gap-[3vh] rounded-2xl bg-night-800 px-[3vh]"
        :class="{ 'ring-2 ring-ember-500': row.rank === 1 }"
      >
        <span class="w-[7vh] text-center text-[4vh] font-black text-ember-500 tabular-nums">{{ medal(row.rank) }}</span>
        <span class="flex-1 truncate text-[4vh] font-bold">{{ row.name }}</span>
        <span class="text-[4vh] font-black tabular-nums">{{ fmt(row.meters) }} m</span>
      </li>
      <li v-if="board && board.rows.length === 0" class="m-auto text-[4vh] text-ember-50/60">
        Nog niemand geklommen. Wie zet de eerste meters?
      </li>
    </ol>
  </main>
</template>
