<template>
  <router-view />
  <Toasts />
  <DialogHost />
</template>

<script setup lang="ts">
import DialogHost from './ui/DialogHost.vue'
import Toasts from './ui/Toasts.vue'
</script>
