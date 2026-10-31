<template>
  <div v-if="open" class="mask" @click.self="emit('close')">
    <div class="modal" :style="{ maxWidth: width }">
      <div class="modal-hd">
        <h3>{{ title }}</h3>
        <button class="close" type="button" aria-label="关闭" @click="emit('close')">✕</button>
      </div>
      <div class="modal-bd">
        <slot />
      </div>
      <div class="modal-ft">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ open: boolean; title: string; width?: string }>(), {
  width: '460px'
})
const emit = defineEmits<{ (e: 'close'): void }>()
</script>
