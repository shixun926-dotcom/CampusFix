<template>
  <div class="sec" v-if="visible">
    <div class="sec-t">
      确认验收
      <span v-if="info?.autoConfirmed" class="sec-hint">系统自动验收</span>
    </div>

    <!-- 倒计时 / 已完成 -->
    <div class="deadline" :class="deadlineClass">
      <span class="dl-ic">
        <Icon :name="isDone ? 'check' : info?.overdue ? 'alert' : 'clock'" :size="16" />
      </span>
      <div class="dl-main">
        <div class="dl-t">{{ deadlineTitle }}</div>
        <div class="dl-d">{{ deadlineDesc }}</div>
      </div>
    </div>

    <!-- 报修人：申请延期 -->
    <div v-if="canApply" class="ext-act">
      <button v-if="!formOpen" class="btn btn-sm" @click="openForm">
        <Icon name="clock" :size="14" />申请延期确认
      </button>
      <div v-else class="ext-form">
        <label class="lbl">延期原因 <em>*</em></label>
        <textarea
          v-model="reason"
          class="field"
          rows="3"
          maxlength="500"
          placeholder="如：在外地实习 / 住院 / 无法当面验收，请说明具体情况"
        />
        <div v-if="formError" class="form-err">{{ formError }}</div>
        <div class="ext-form-act">
          <button class="btn btn-sm" @click="formOpen = false">取消</button>
          <button class="btn btn-sm btn-primary" :disabled="busy" @click="onApply">提交申请</button>
        </div>
      </div>
    </div>

    <!-- 管理员：待审批 -->
    <div v-if="pending && canHandle" class="ext-pending">
      <div class="ep-hd">
        <span class="tag tag-warn">待审批</span>
        <span class="ep-who">{{ pending.studentName }} · {{ pending.createdAt }}</span>
      </div>
      <div class="ep-reason">{{ pending.reason }}</div>
      <div class="ep-act">
        <label class="ep-days">
          顺延
          <input v-model.number="extraDays" type="number" min="1" max="30" class="field" />
          天
        </label>
        <span class="spacer" />
        <button class="btn btn-sm" :disabled="busy" @click="onHandle(false)">驳回</button>
        <button class="btn btn-sm btn-primary" :disabled="busy" @click="onHandle(true)">通过</button>
      </div>
    </div>

    <!-- 申请记录 -->
    <div v-if="info?.extensions?.length" class="ext-list">
      <div v-for="e in info.extensions" :key="e.id" class="ext-item">
        <span class="tag" :class="extTagClass(e.status)">{{ e.statusLabel }}</span>
        <div class="ei-main">
          <div class="ei-reason">{{ e.reason }}</div>
          <div class="ei-meta">
            {{ e.studentName || '—' }} 申请 · {{ e.createdAt }}
            <template v-if="e.handleResult">
              <br />处理：{{ e.handleResult }}（{{ e.handlerName || '管理员' }}）
            </template>
          </div>
        </div>
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
  applyExtension,
  getAcceptance,
  handleExtension,
  type AcceptanceVO,
  type ExtensionVO,
  type OrderVO
} from '../api/order'

const props = defineProps<{ order: OrderVO; orderId: number }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const auth = useAuthStore()
const info = ref<AcceptanceVO | null>(null)
const busy = ref(false)
const formOpen = ref(false)
const reason = ref('')
const formError = ref('')
const extraDays = ref(3)

const meId = computed(() => Number(auth.user?.id || 0))
const isReporter = computed(() => meId.value > 0 && props.order.reporterId === meId.value)
const isManager = computed(() => auth.hasRole('MANAGER') || auth.hasRole('ADMIN'))
const isDone = computed(() => props.order.status === 'COMPLETED')

/** 待确认、或已完成但属于超时自动验收时，才展示这一块 */
const visible = computed(
  () => props.order.status === 'PENDING_CONFIRM' || (isDone.value && !!info.value?.autoConfirmed)
)
const canApply = computed(
  () => isReporter.value && props.order.status === 'PENDING_CONFIRM' && !info.value?.hasPendingExtension
)
const canHandle = computed(() => isManager.value)
const pending = computed<ExtensionVO | null>(
  () => info.value?.extensions?.find((e) => e.status === 'PENDING') || null
)

const deadlineClass = computed(() => ({
  done: isDone.value,
  overdue: !isDone.value && !!info.value?.overdue,
  warn: !isDone.value && !info.value?.overdue && (info.value?.hoursLeft ?? 99) <= 24
}))

const deadlineTitle = computed(() => {
  if (isDone.value) {
    return info.value?.autoConfirmed ? '超时未确认，已由系统自动验收' : '报修人已确认完成'
  }
  if (info.value?.overdue) return '已超过自动验收时限，将很快由系统自动完成'
  const h = info.value?.hoursLeft
  if (h == null) return '等待报修人确认'
  return h >= 24 ? `剩余约 ${Math.floor(h / 24)} 天 ${h % 24} 小时自动验收` : `剩余约 ${h} 小时自动验收`
})

const deadlineDesc = computed(() => {
  const days = info.value?.autoConfirmDays ?? 3
  if (isDone.value) return info.value?.autoConfirmed ? '报修人信誉分已按规则扣减' : '工单已归档，可提交评价'
  if (info.value?.hasPendingExtension) return '延期申请待管理员审批，审批期间倒计时照常计算'
  return `提交维修结果后 ${days} 天内未确认，系统将自动完成并扣减报修人信誉分`
})

const TAG: Record<string, string> = { PENDING: 'tag-warn', APPROVED: 'tag-ok', REJECTED: 'tag-crit' }
const extTagClass = (s: string) => TAG[s] || ''

async function load() {
  try {
    const data: any = await getAcceptance(props.orderId)
    info.value = data || null
    if (data?.extensionDays) extraDays.value = data.extensionDays
  } catch {
    info.value = null
  }
}

function openForm() {
  formOpen.value = true
  reason.value = ''
  formError.value = ''
}

async function onApply() {
  if (!reason.value.trim()) {
    formError.value = '请填写延期原因'
    return
  }
  busy.value = true
  try {
    await applyExtension(props.orderId, reason.value.trim())
    toastOk('延期申请已提交，等待管理员审批')
    formOpen.value = false
    await load()
    emit('changed')
  } catch (e: any) {
    formError.value = e?.message || '提交失败'
  } finally {
    busy.value = false
  }
}

async function onHandle(approve: boolean) {
  const target = pending.value
  if (!target) return
  const ok = await confirmDialog({
    title: approve ? '通过延期申请' : '驳回延期申请',
    message: approve
      ? `通过后自动验收截止时间顺延 ${extraDays.value} 天，报修人可继续确认。`
      : '驳回后倒计时不变，到期仍会自动验收并扣减信誉分。',
    okText: approve ? '确认通过' : '确认驳回',
    danger: !approve
  })
  if (!ok) return
  busy.value = true
  try {
    await handleExtension(target.id, {
      approve,
      extraDays: approve ? extraDays.value : undefined
    })
    toastOk(approve ? '已通过延期' : '已驳回延期')
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
.deadline {
  display: flex;
  gap: 10px;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 11px 13px;
  background: #fcfcfd;
}
.deadline.warn {
  border-color: #ffe2b0;
  background: #fffaf0;
}
.deadline.overdue {
  border-color: #ffd0d0;
  background: #fff6f6;
}
.deadline.done {
  border-color: #bfe6cd;
  background: #f6fdf9;
}
.dl-ic {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--bg);
  color: var(--ink-2);
  flex: none;
}
.deadline.warn .dl-ic {
  background: #fff3d6;
  color: #b7791f;
}
.deadline.overdue .dl-ic {
  background: #ffe6e6;
  color: var(--crit);
}
.deadline.done .dl-ic {
  background: #e6f7ec;
  color: #12a150;
}
.dl-t {
  font-size: 13px;
  font-weight: 600;
}
.dl-d {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 2px;
  line-height: 1.6;
}
.ext-act {
  margin-top: 12px;
}
.ext-form {
  border: 1px dashed #cfd3da;
  border-radius: 10px;
  padding: 12px;
  background: #fafbfc;
}
textarea.field {
  resize: vertical;
  height: auto;
  padding: 9px 11px;
  font-family: inherit;
  line-height: 1.6;
}
.lbl em {
  color: var(--brand);
  font-style: normal;
}
.ext-form-act {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 10px;
}
.ext-pending {
  margin-top: 12px;
  border: 1px solid #ffe2b0;
  background: #fffaf0;
  border-radius: 10px;
  padding: 11px 13px;
}
.ep-hd {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ep-who {
  font-size: 12px;
  color: var(--ink-3);
}
.ep-reason {
  font-size: 12.5px;
  color: var(--ink-2);
  margin-top: 6px;
  line-height: 1.65;
}
.ep-act {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.ep-days {
  font-size: 12.5px;
  color: var(--ink-2);
  display: flex;
  align-items: center;
  gap: 5px;
}
.ep-days .field {
  width: 62px;
  height: 32px;
  text-align: center;
}
.spacer {
  flex: 1;
}
.ext-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ext-item {
  display: flex;
  gap: 9px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 9px 11px;
}
.ei-main {
  flex: 1;
}
.ei-reason {
  font-size: 12.5px;
  color: var(--ink);
  line-height: 1.6;
}
.ei-meta {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 3px;
  line-height: 1.6;
}
</style>
