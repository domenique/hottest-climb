<script setup lang="ts">
import type { LeaderboardRow } from '../api'

defineProps<{ row: LeaderboardRow; compact?: boolean }>()

const fmt = (n: number) => n.toLocaleString('nl-BE')
const medal = (rank: number) => ['🥇', '🥈', '🥉'][rank - 1] ?? rank
const joined = (names: string[]) =>
  names.length > 1 ? `${names.slice(0, -1).join(', ')} & ${names.at(-1)}` : (names[0] ?? '')
</script>

<template>
  <li
    class="flex shrink-0 items-center rounded-2xl bg-night-800"
    :class="[
      compact ? 'h-[calc(5.8*var(--u))] gap-[calc(2.5*var(--u))] px-[calc(2.5*var(--u))] text-[calc(2.8*var(--u))]' : 'h-[calc(8.5*var(--u))] gap-[calc(3*var(--u))] px-[calc(3*var(--u))] text-[calc(4*var(--u))]',
      { 'ring-2 ring-ember-500 ring-inset': row.rank === 1 },
    ]"
  >
    <span class="text-center font-black text-ember-500 tabular-nums" :class="compact ? 'w-[calc(5*var(--u))]' : 'w-[calc(7*var(--u))]'">
      {{ medal(row.rank) }}
    </span>
    <span class="flex-1 truncate font-bold" :title="joined(row.names)">{{ joined(row.names) }}</span>
    <span class="font-black tabular-nums">{{ fmt(row.meters) }} m</span>
  </li>
</template>
