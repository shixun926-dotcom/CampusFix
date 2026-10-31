<template>
  <div v-if="dialogState.open" class="mask" @click.self="closeDialog(false)">
    <div class="modal">
      <div class="modal-hd"><h3>{{ dialogState.title }}</h3></div>
      <div class="modal-bd">{{ dialogState.message }}</div>
      <div class="modal-ft">
        <button class="btn" @click="closeDialog(false)">{{ dialogState.cancelText }}</button>
        <button class="btn" :class="dialogState.danger ? 'btn-danger' : 'btn-primary'" @click="closeDialog(true)">
          {{ dialogState.okText }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { closeDialog, dialogState } from './dialog'
</script>
