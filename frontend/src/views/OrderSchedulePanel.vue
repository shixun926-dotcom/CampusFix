<template>
  <div class="sec">
    <div class="sec-t">
      维修预约
      <span v-if="order.planTime" class="sec-hint">约定上门 {{ order.planTime }}</span>
    </div>

    <div v-if="loading" class="empty-line">加载中…</div>

    <div v-else-if="schedules.length === 0" class="empty-line">
      还没有时段。维修人员接单后需先给出可上门时间，报修人再从中选择。
    </div>

    <div v-else class="slot-list">
      <div v-for="s in schedules" :key="s.id" class="slot" :class="'is-' + s.status.toLowerCase()">
        <div class="slot-main">
          <span class="slot-date">{{ s.planDate }}</span>
          <span class="slot-time">{{ s.slotStart }}–{{ s.slotEnd }}</span>
          <span v-if="s.reschedule" class="tag tag-warn">改约</span>
          <span class="tag" :class="tagClass(s.status)">{{ s.statusLabel }}</span>
          <span class="spacer" />
          <span v-if="s.repairerName" class="slot-who">{{ s.repairerName }}</span>
        </div>
        <div v-if="s.remark" class="slot-remark">{{ s.remark }}</div>
        <div v-if="s.selectable || s.acceptable || canConfirm(s)" class="slot-act">
          <button v-if="s.selectable" class="btn btn-sm btn-primary" :disabled="busy" @click="onSelect(s)">
            选这个时间
          </button>
          <button v-if="s.acceptable" class="btn btn-sm btn-primary" :disabled="busy" @click="onAccept(s)">
            接受改约
          </button>
          <button v-if="s.acceptable" class="btn btn-sm btn-danger" :disabled="busy" @click="onReject(s)">
            不接受
          </button>
          <button v-if="canConfirm(s)" class="btn btn-sm btn-primary" :disabled="busy" @click="onConfirm(s)">
            确认该时段
          </button>
        </div>
      </div>
    </div>

    <!-- 维修工：给时段 / 改约 -->
    <div v-if="canOffer || canReschedule" class="slot-ops">
      <button v-if="!formOpen" class="btn btn-sm" @click="openForm(false)">
        <Icon name="clock" :size="14" />{{ canOffer ? '提供可上门时段' : '申请改约' }}
      </button>

      <div v-if="formOpen" class="slot-form">
        <div class="slot-form-t">
          {{ rescheduleMode ? '改约提议（报修人需确认）' : '可上门时段（1–3 个，供报修人选择）' }}
        </div>
        <div v-for="(row, i) in formRows" :key="i" class="slot-row">
          <input v-model="row.planDate" type="date" class="field" />
          <input v-model="row.slotStart" type="time" class="field" />
          <span class="tilde">—</span>
          <input v-model="row.slotEnd" type="time" class="field" />
          <button
            v-if="!rescheduleMode && formRows.length > 1"
            class="row-del"
            type="button"
            title="删除这一行"
            @click="formRows.splice(i, 1)"
          >
            ✕
          </button>
        </div>
        <div class="slot-form-act">
          <button v-if="!rescheduleMode && formRows.length < 3" class="btn btn-sm" @click="addRow">
            <Icon name="plus" :size="14" />再加一个
          </button>
          <span class="spacer" />
          <button class="btn btn-sm" @click="formOpen = false">取消</button>
          <button class="btn btn-sm btn-primary" :disabled="busy" @click="onSubmitForm">
            {{ rescheduleMode ? '提交改约' : '提交时段' }}
          </button>
        </div>
        <div v-if="formError" class="form-err">{{ formError }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Icon from '../ui/Icon.vue'
import { confirmDialog } from '../ui/dialog'
import { toastErr, toastOk } from '../ui/toast'
import { useAuthStore } from '../stores/auth'
import {
  acceptSchedule,
  confirmSchedule,
  listSchedules,
  offerSchedules,
  rejectSchedule,
  rescheduleOrder,
  selectSchedule,
  type OrderVO,
  type ScheduleVO,
  type SlotPayload
} from '../api/order'

const props = defineProps<{ order: OrderVO; orderId: number }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const auth = useAuthStore()
const schedules = ref<ScheduleVO[]>([])
const loading = ref(false)
const busy = ref(false)

const formOpen = ref(false)
const rescheduleMode = ref(false)
const formRows = ref<SlotPayload[]>([])
const formError = ref('')

const meId = computed(() => Number(auth.user?.id || 0))
const isAssignee = computed(() => meId.value > 0 && props.order.assigneeId === meId.value)
const isReporter = computed(() => meId.value > 0 && props.order.reporterId === meId.value)

const canOffer = computed(() => isAssignee.value && props.order.status === 'ACCEPTED')
const canReschedule = computed(
  () => isAssignee.value && (props.order.status === 'ACCEPTED' || props.order.status === 'PROCESSING')
)

function canConfirm(s: ScheduleVO) {
  return isAssignee.value && s.status === 'SELECTED' && props.order.status === 'ACCEPTED'
}

const TAG_CLASS: Record<string, string> = {
  OFFERED: 'tag-brand',
  SELECTED: 'tag-warn',
  CONFIRMED: 'tag-ok',
  CANCELLED: 'tag-ink'
}
const tagClass = (status: string) => TAG_CLASS[status] || ''

async function load() {
  loading.value = true
  try {
    const data: any = await listSchedules(props.orderId)
    schedules.value = data || []
  } catch {
    schedules.value = []
  } finally {
    loading.value = false
  }
}

function openForm(reschedule: boolean) {
  rescheduleMode.value = reschedule
  formOpen.value = true
  formError.value = ''
  formRows.value = [{ planDate: '', slotStart: '', slotEnd: '' }]
}

function addRow() {
  formRows.value.push({ planDate: '', slotStart: '', slotEnd: '' })
}

async function onSubmitForm() {
  formError.value = ''
  const rows = formRows.value.filter((r) => r.planDate && r.slotStart && r.slotEnd)
  if (rows.length === 0) {
    formError.value = '请填写完整的日期与起止时间'
    return
  }
  if (rescheduleMode.value) {
    rows.splice(1)
  }

  busy.value = true
  try {
    if (rescheduleMode.value) {
      await rescheduleOrder(props.orderId, rows[0])
      toastOk('已提交改约，等待报修人确认')
    } else {
      await offerSchedules(props.orderId, rows)
      toastOk('已提交可上门时段')
    }
    formOpen.value = false
    await load()
    emit('changed')
  } catch (e: any) {
    formError.value = e?.message || '提交失败'
  } finally {
    busy.value = false
  }
}

async function onSelect(s: ScheduleVO) {
  busy.value = true
  try {
    await selectSchedule(s.id)
    toastOk('已选择，等待维修人员确认')
    await load()
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '选择失败')
  } finally {
    busy.value = false
  }
}

async function onConfirm(s: ScheduleVO) {
  busy.value = true
  try {
    await confirmSchedule(s.id)
    toastOk('已确认上门时间，工单进入处理中')
    await load()
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '确认失败')
  } finally {
    busy.value = false
  }
}

async function onAccept(s: ScheduleVO) {
  busy.value = true
  try {
    await acceptSchedule(s.id)
    toastOk('已接受改约')
    await load()
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '接受失败')
  } finally {
    busy.value = false
  }
}

async function onReject(s: ScheduleVO) {
  const ok = await confirmDialog({
    title: '拒绝改约',
    message: '拒绝后本单会退回抢单池重新分配维修人员，需要重新预约。确定继续？',
    okText: '确认拒绝',
    danger: true
  })
  if (!ok) return
  const reason = window.prompt('请说明拒绝原因（选填）') || ''
  busy.value = true
  try {
    await rejectSchedule(s.id, reason)
    toastOk('已拒绝改约，工单退回抢单池')
    await load()
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

onMounted(load)
watch(() => props.orderId, load)
</script>

<style scoped>
.sec-hint {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--brand);
}
.empty-line {
  font-size: 12.5px;
  color: var(--ink-3);
  background: var(--bg);
  border-radius: 8px;
  padding: 12px 14px;
  line-height: 1.7;
}
.slot-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.slot {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  background: #fff;
}
.slot.is-confirmed {
  border-color: #bfe6cd;
  background: #f6fdf9;
}
.slot.is-cancelled {
  opacity: 0.62;
}
.slot-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.slot-date {
  font-weight: 600;
}
.slot-time {
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
}
.slot-who {
  font-size: 12px;
  color: var(--ink-3);
}
.slot-remark {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 4px;
}
.slot-act {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}
.slot-ops {
  margin-top: 10px;
}
.slot-form {
  margin-top: 10px;
  border: 1px dashed #cfd3da;
  border-radius: 10px;
  padding: 12px;
  background: #fafbfc;
}
.slot-form-t {
  font-size: 12.5px;
  color: var(--ink-2);
  margin-bottom: 8px;
}
.slot-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.slot-row .field {
  height: 36px;
}
.slot-row input[type='date'] {
  flex: 1.4;
  min-width: 120px;
}
.slot-row input[type='time'] {
  flex: 1;
  min-width: 92px;
}
.tilde {
  color: var(--ink-3);
}
.row-del {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  color: var(--ink-3);
  cursor: pointer;
}
.row-del:hover {
  background: var(--brand-l);
  color: var(--brand);
}
.slot-form-act {
  display: flex;
  align-items: center;
  gap: 6px;
}
.spacer {
  flex: 1;
}
</style>
