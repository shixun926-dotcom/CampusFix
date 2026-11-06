<template>
  <span class="tag" :class="cls">
    <i class="dot" :style="{ background: color }" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string; label?: string }>()

/** 状态 → 标签配色：标签统一石板灰底（tag-slate），状态语义由前置圆点颜色承载 */
const MAP: Record<string, { cls: string; text: string; color: string }> = {
  PENDING_REVIEW: { cls: 'tag-slate', text: '待审核', color: '#f59e0b' },
  PENDING: { cls: 'tag-slate', text: '待处理', color: '#e63946' },
  ASSIGNED: { cls: 'tag-slate', text: '已分配', color: '#8a90a0' },
  ACCEPTED: { cls: 'tag-slate', text: '已接单', color: '#4f6ef7' },
  PROCESSING: { cls: 'tag-slate', text: '处理中', color: '#4f6ef7' },
  SUSPENDED: { cls: 'tag-slate', text: '挂起', color: '#dc3545' },
  PENDING_CONFIRM: { cls: 'tag-slate', text: '待确认', color: '#f59e0b' },
  COMPLETED: { cls: 'tag-slate', text: '已完成', color: '#12a150' },
  REJECTED: { cls: 'tag-slate', text: '已驳回', color: '#dc3545' },
  CANCELLED: { cls: 'tag-slate', text: '已取消', color: '#9aa3b2' }
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
