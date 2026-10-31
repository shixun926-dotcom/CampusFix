<template>
  <svg
    class="ui-ic"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="path"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 18 })

/** 线性图标：统一 24×24 视框、1.7 描边，颜色继承父级 color */
const ICONS: Record<string, string> = {
  grid: '<rect x="3.6" y="3.6" width="7" height="7" rx="1.4"/><rect x="13.4" y="3.6" width="7" height="7" rx="1.4"/><rect x="3.6" y="13.4" width="7" height="7" rx="1.4"/><rect x="13.4" y="13.4" width="7" height="7" rx="1.4"/>',
  users: '<circle cx="9" cy="8.4" r="3.2"/><path d="M3.4 19.6c0-3 2.5-4.9 5.6-4.9s5.6 1.9 5.6 4.9"/><path d="M16.2 5.6a3.2 3.2 0 0 1 0 6"/><path d="M17.6 14.9c1.9.5 3.1 1.9 3.1 3.9"/>',
  user: '<circle cx="12" cy="8.2" r="3.6"/><path d="M4.9 20c0-3.6 3.2-5.8 7.1-5.8s7.1 2.2 7.1 5.8"/>',
  receipt: '<path d="M6 3.6h12v16.8l-3-1.8-3 1.8-3-1.8-3 1.8V3.6Z"/><path d="M9 8h6M9 12h6"/>',
  chart: '<path d="M4 20h16"/><path d="M7.5 20v-6.4M12 20V7.6M16.5 20v-9.4"/>',
  search: '<circle cx="10.8" cy="10.8" r="6.3"/><path d="M15.6 15.6 20.5 20.5"/>',
  logout: '<path d="M14.5 4.5H19a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5h-4.5"/><path d="M10 8.5 6 12l4 3.5"/><path d="M6 12h9"/>',
  key: '<circle cx="8.5" cy="12" r="3.5"/><path d="M12 12h8.5"/><path d="M16.8 12v3M19.6 12v2"/>',
  lock: '<rect x="4.6" y="10" width="14.8" height="10.4" rx="2"/><path d="M8.2 10V7.6a3.8 3.8 0 0 1 7.6 0V10"/>',
  check: '<circle cx="12" cy="12" r="8.6"/><path d="M8.4 12.2l2.5 2.5 4.7-5"/>',
  alert: '<path d="M12 3.6 21 19.2H3L12 3.6Z"/><path d="M12 9.6v4.2"/><path d="M12 16.8h.01"/>',
  clock: '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.6V12l3.1 1.8"/>',
  bolt: '<path d="M13.2 2.8 5.6 13.4h5.2l-1 7.8 7.6-10.6h-5.2l1-7.8Z"/>',
  shield: '<path d="M12 2.8 4.9 5.7v5.2c0 4.5 3 7.8 7.1 9.5 4.1-1.7 7.1-5 7.1-9.5V5.7L12 2.8Z"/><path d="M8.9 11.9l2.2 2.2 4-4.2"/>',
  file: '<path d="M13.5 3.5H7A1.6 1.6 0 0 0 5.4 5.1v13.8A1.6 1.6 0 0 0 7 20.5h10a1.6 1.6 0 0 0 1.6-1.6V8.6L13.5 3.5Z"/><path d="M13.5 3.5v5.1h5.1"/><path d="M8.8 13h6.4M8.8 16.4h4.4"/>',
  arrowRight: '<path d="M4.6 12h14"/><path d="M13 6.4l5.6 5.6-5.6 5.6"/>',
  arrowUpRight: '<path d="M7 17 17 7"/><path d="M9 7h8v8"/>',
  refresh: '<path d="M19.6 12a7.6 7.6 0 1 1-2.2-5.4"/><path d="M19.8 3.9v4.3h-4.3"/>',
  dot: '<circle cx="12" cy="12" r="7"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  bell: '<path d="M18 9.4a6 6 0 0 0-12 0c0 4.6-1.6 5.8-1.6 5.8h15.2S18 14 18 9.4Z"/><path d="M10.1 19a2.1 2.1 0 0 0 3.8 0"/>',
  chevronDown: '<path d="M6.5 9.5 12 15l5.5-5.5"/>',
  eye: '<path d="M2.8 12S6.3 5.8 12 5.8 21.2 12 21.2 12 17.7 18.2 12 18.2 2.8 12 2.8 12Z"/><circle cx="12" cy="12" r="3.1"/>',
  eyeOff: '<path d="M4 4l16 16"/><path d="M9.9 6.1A9.8 9.8 0 0 1 12 5.8c5.7 0 9.2 6.2 9.2 6.2a17 17 0 0 1-2.9 3.7"/><path d="M6.2 7.7A17 17 0 0 0 2.8 12S6.3 18.2 12 18.2c1.4 0 2.7-.3 3.8-.8"/><path d="M10.2 10.3a2.5 2.5 0 0 0 3.5 3.5"/>',
  pin: '<path d="M12 21s6.2-5.3 6.2-10.2A6.2 6.2 0 0 0 5.8 10.8C5.8 15.7 12 21 12 21Z"/><circle cx="12" cy="10.6" r="2.2"/>',
  wrench: '<path d="M14.8 6.2a4.2 4.2 0 0 1 5.2-1.1l-2.4 2.4 1.8 1.8 2.4-2.4a4.2 4.2 0 0 1-5.3 5.1l-6.6 6.6a2 2 0 0 1-2.8-2.8l6.6-6.6a4.2 4.2 0 0 1 1.1-3Z"/>',
  sparkles: '<path d="M12 4.2l1.5 4.1 4.1 1.5-4.1 1.5L12 15.4l-1.5-4.1-4.1-1.5 4.1-1.5L12 4.2Z"/><path d="M18.6 15.2l.8 2.1 2.1.8-2.1.8-.8 2.1-.8-2.1-2.1-.8 2.1-.8.8-2.1Z"/>',
  calendar: '<rect x="3.8" y="5.4" width="16.4" height="15" rx="2.4"/><path d="M8 3.4v4M16 3.4v4M3.8 10h16.4"/>',
  filter: '<path d="M4 5.5h16l-6.2 7.2v5.2l-3.6 1.8v-7L4 5.5Z"/>',
  building: '<path d="M4.5 20.5V5.2a1.6 1.6 0 0 1 1.6-1.6h7.2a1.6 1.6 0 0 1 1.6 1.6v15.3"/><path d="M14.9 9h3.1a1.6 1.6 0 0 1 1.6 1.6v9.9"/><path d="M2.8 20.5h18.4"/><path d="M8 7.2h2.4M8 10.8h2.4M8 14.4h2.4"/>',
  phone: '<path d="M7.1 3.8h2.6l1.2 3.5-1.8 1.4a11.4 11.4 0 0 0 5.2 5.2l1.4-1.8 3.5 1.2v2.6a2 2 0 0 1-2.1 2A15.5 15.5 0 0 1 5.1 5.9a2 2 0 0 1 2-2.1Z"/>',
  play: '<circle cx="12" cy="12" r="8.6"/><path d="M10 8.8l5.2 3.2L10 15.2V8.8Z"/>',
  droplet: '<path d="M12 3.2s6 6.1 6 10.1a6 6 0 0 1-12 0c0-4 6-10.1 6-10.1Z"/><path d="M9.3 14.1a2.8 2.8 0 0 0 2.5 2.6"/>',
  wifi: '<path d="M3.6 9.4a12.3 12.3 0 0 1 16.8 0"/><path d="M6.7 12.7a7.8 7.8 0 0 1 10.6 0"/><path d="M9.7 15.9a3.6 3.6 0 0 1 4.6 0"/><path d="M12 19h.01"/>',
  snowflake: '<path d="M12 2.8v18.4"/><path d="M3.9 7.4l16.2 9.2"/><path d="M20.1 7.4 3.9 16.6"/><path d="M9.4 4.9 12 7.5l2.6-2.6"/><path d="M9.4 19.1 12 16.5l2.6 2.6"/>',
  door: '<rect x="5.5" y="3.5" width="13" height="17" rx="1.6"/><path d="M15.2 11.8h.01"/><path d="M3.6 20.5h16.8"/>',
  lightbulb: '<path d="M12 3.2a6 6 0 0 0-3.4 10.9c.7.5 1.1 1.2 1.1 2h4.6c0-.8.4-1.5 1.1-2A6 6 0 0 0 12 3.2Z"/><path d="M9.8 19h4.4"/><path d="M10.6 21h2.8"/>',
  sofa: '<path d="M4.5 11V7.4a1.9 1.9 0 0 1 1.9-1.9h11.2a1.9 1.9 0 0 1 1.9 1.9V11"/><path d="M4.5 11a1.6 1.6 0 0 1 3.2 0v2.8h8.6V11a1.6 1.6 0 0 1 3.2 0v5.3a1.7 1.7 0 0 1-1.7 1.7H6.2a1.7 1.7 0 0 1-1.7-1.7V11Z"/><path d="M6.2 18v2M17.8 18v2"/>',
  chat: '<path d="M20.4 11.6c0 4-3.8 7.2-8.4 7.2-1.2 0-2.4-.2-3.4-.7L4 19.6l1.4-3.9a6.8 6.8 0 0 1-1.8-4.1C3.6 7.6 7.4 4.4 12 4.4s8.4 3.2 8.4 7.2Z"/><path d="M8.4 11.6h.01M12 11.6h.01M15.6 11.6h.01"/>',
  send: '<path d="M20.5 3.5 3.5 10.2l6.3 2.6 2.6 6.3 8.1-15.6Z"/><path d="M9.8 12.8 20.5 3.5"/>',
  read: '<circle cx="12" cy="12" r="8.6"/><path d="M8.4 12.2l2.5 2.5 4.7-5"/>'
}

const path = computed(() => ICONS[props.name] || ICONS.dot)
</script>
