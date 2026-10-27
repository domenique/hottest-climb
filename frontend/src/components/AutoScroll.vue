<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

// Seconds to scroll one full screen height; speed then scales with the monitor's resolution.
const SECONDS_PER_SCREEN = 12
const PAUSE_MS = 4000
const FADE_MS = 600

type Phase = 'top' | 'scrolling' | 'bottom' | 'fading'

const viewport = ref<HTMLElement>()
const content = ref<HTMLElement>()
const faded = ref(false)

let phase: Phase = 'top'
let phaseStart = 0
let offset = 0
let lastFrame = 0
let frame = 0

function enter(next: Phase, now: number) {
  phase = next
  phaseStart = now
}

// Measures overflow every frame, so polled data changing the list length never restarts the cycle.
function tick(now: number) {
  frame = requestAnimationFrame(tick)
  const dt = lastFrame ? (now - lastFrame) / 1000 : 0
  lastFrame = now
  const view = viewport.value
  const list = content.value
  if (!view || !list) return

  const max = Math.max(0, list.scrollHeight - view.clientHeight)
  const elapsed = now - phaseStart

  if (max === 0) {
    offset = 0
    enter('top', now)
  } else if (phase === 'top' && elapsed > PAUSE_MS) {
    enter('scrolling', now)
  } else if (phase === 'scrolling') {
    offset = Math.min(max, offset + (view.clientHeight / SECONDS_PER_SCREEN) * dt)
    if (offset >= max) enter('bottom', now)
  } else if (phase === 'bottom' && elapsed > PAUSE_MS) {
    faded.value = true
    enter('fading', now)
  } else if (phase === 'fading' && elapsed > FADE_MS) {
    offset = 0
    faded.value = false
    enter('top', now)
  }

  list.style.transform = `translateY(${-offset}px)`
}

onMounted(() => {
  frame = requestAnimationFrame((now) => {
    phaseStart = now
    tick(now)
  })
})
onUnmounted(() => cancelAnimationFrame(frame))
</script>

<template>
  <div ref="viewport" class="min-h-0 flex-1 overflow-hidden">
    <div
      ref="content"
      class="transition-opacity will-change-transform"
      :class="faded ? 'opacity-0' : 'opacity-100'"
      :style="{ transitionDuration: `${FADE_MS}ms` }"
    >
      <slot />
    </div>
  </div>
</template>
