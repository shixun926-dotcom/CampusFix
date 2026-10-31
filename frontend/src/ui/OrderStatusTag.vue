<template>
  <span class="tag" :class="cls">
    <i class="dot" :style="{ background: color }" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string; label?: string }>()

/** 状态 → 标签配色：进行中偏品牌色、终态偏中性、异常偏警示 */
const MAP: Record<string, { cls: string; text: string; color: string }> = {
  PENDING_REVIEW: { cls: 'tag-warn', text: '待审核', color: '#f59e0b' },
  PENDING: { cls: 'tag-brand', text: '待处理', color: '#e63946' },
  ASSIGNED: { cls: 'tag-ink', text: '已分配', color: '#8a90a0' },
  ACCEPTED: { cls: 'tag-ink', text: '已接单', color: '#4f6ef7' },
  PROCESSING: { cls: 'tag-brand', text: '处理中', color: '#4f6ef7' },
  SUSPENDED: { cls: 'tag-crit', text: '挂起', color: '#dc3545' },
  PENDING_CONFIRM: { cls: 'tag-warn', text: '待确认', color: '#f59e0b' },
  COMPLETED: { cls: 'tag-ok', text: '已完成', color: '#12a150' },
  REJECTED: { cls: 'tag-crit', text: '已驳回', color: '#dc3545' },
  CANCELLED: { cls: 'tag', text: '已取消', color: '#9aa3b2' }
}

const cls = computed(() => MAP[props.status]?.cls || '')
const label = computed(() => props.label || MAP[props.status]?.text || props.status)
const color = computed(() => MAP[props.status]?.color || '#8a90a0')
</script>

<style scoped>
.tag {
  height: 24px;
  padding: 0 9px;
  gap: 5px;
  border-radius: 7px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  opacity: 0.9;
}
</style>
