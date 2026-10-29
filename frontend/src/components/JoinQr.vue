<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QRCode from 'qrcode'

// The monitor may be opened via localhost, which is useless to a phone; set VITE_PUBLIC_URL at build time.
const joinUrl = new URL('/', import.meta.env.VITE_PUBLIC_URL || window.location.origin).href
const displayUrl = joinUrl.replace(/^https?:\/\//, '').replace(/\/$/, '')
const svg = ref('')

onMounted(async () => {
  svg.value = await QRCode.toString(joinUrl, { margin: 1, color: { dark: '#0e110d', light: '#ffffff' } })
})
</script>

<template>
  <div class="flex items-center gap-[calc(2.5*var(--u))]">
    <div class="text-right">
      <p class="text-[calc(3*var(--u))] leading-tight font-black">Klim mee!</p>
      <p class="text-[calc(2*var(--u))] font-semibold">Scan &amp; log je meters</p>
      <p class="mt-[calc(0.5*var(--u))] text-[calc(1.8*var(--u))] font-medium opacity-80">{{ displayUrl }}</p>
    </div>
    <!-- eslint-disable-next-line vue/no-v-html -- SVG generated locally by qrcode -->
    <div class="aspect-square h-[calc(17*var(--u))] rounded-2xl bg-white p-[calc(0.8*var(--u))] [&>svg]:h-full [&>svg]:w-full" v-html="svg" />
  </div>
</template>
