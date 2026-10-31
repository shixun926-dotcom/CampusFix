<template>
  <div class="toasts">
    <div v-for="t in toastState.items" :key="t.id" class="toast" :class="t.type">
      <Icon :name="t.type === 'ok' ? 'check' : t.type === 'err' ? 'alert' : 'dot'" :size="15" />
      <span>{{ t.msg }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import Icon from './Icon.vue'
import { toastState } from './toast'
</script>
