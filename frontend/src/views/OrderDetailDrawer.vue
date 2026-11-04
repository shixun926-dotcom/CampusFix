<template>
  <div v-if="open" class="drawer-mask" @click.self="close">
    <div class="drawer">
      <div class="drawer-hd">
        <div>
          <h3>{{ order?.title || '工单详情' }}</h3>
          <div class="meta" v-if="order">
            <span class="mono">{{ order.orderNo }}</span>
            <span class="dot-sep">·</span>
            <OrderStatusTag :status="order.status" :label="order.statusLabel" />
            <span v-if="order.isWarranty" class="tag tag-ok" style="margin-left: 6px">质保返修</span>
            <span v-if="order.autoAssigned" class="tag tag-warn" style="margin-left: 6px">系统派单</span>
            <span v-if="order.autoConfirmed" class="tag tag-ink" style="margin-left: 6px">系统验收</span>
          </div>
        </div>
        <button class="close" @click="close">✕</button>
      </div>

      <div class="drawer-bd" v-if="order">
        <div class="status-hero">
          <div class="sh-left">
            <span class="sh-ic"><Icon name="receipt" :size="19" /></span>
            <div>
              <div class="sh-label">当前状态</div>
              <div class="sh-value">{{ order.statusLabel }}</div>
            </div>
          </div>
          <div class="sh-stats">
            <div><span>优先级</span><b>{{ order.priority }}</b></div>
            <div><span>紧急程度</span><b>{{ urgencyText }}</b></div>
            <div><span>返修次数</span><b>{{ order.reworkCount || 0 }}</b></div>
          </div>
        </div>

        <div class="kv">
          <div class="k">故障类型</div>
          <div class="v">{{ order.categoryName || '—' }}</div>
          <div class="k">责任部门</div>
          <div class="v">{{ order.deptName || '—' }}</div>
          <div class="k">报修位置</div>
          <div class="v">{{ order.locationPath }}</div>
          <div class="k">报修人</div>
          <div class="v">
            {{ order.reporterName || '—' }}
            <span v-if="order.reporterPhone" class="muted">（{{ order.reporterPhone }}）</span>
          </div>
          <div class="k">维修人</div>
          <div class="v">
            <template v-if="order.assigneeName">
              <div class="repairer-chip">
                <span class="rp-avatar">
                  <img v-if="order.assigneeAvatar" :src="asset(order.assigneeAvatar)" alt="" />
                  <span v-else class="rp-default">{{ (order.assigneeName || '维').charAt(0) }}</span>
                </span>
                <span class="rp-main">
                  <span class="rp-name">
                    {{ order.assigneeName }}
                    <span v-if="order.assigneeStaffNo" class="muted">工号 {{ order.assigneeStaffNo }}</span>
                  </span>
                  <span class="rp-sub">
                    {{ order.assigneeDeptName || '未分组' }}
                    <template v-if="order.assigneePhone"> · {{ order.assigneePhone }}</template>
                  </span>
                </span>
              </div>
            </template>
            <span v-else class="muted">尚未指派</span>
          </div>
          <div class="k">紧急程度</div>
          <div class="v">{{ urgencyText }}　<span class="muted">优先级 {{ order.priority }}</span></div>
          <div class="k">上报时间</div>
          <div class="v">{{ order.createdAt }}</div>
          <div class="k" v-if="order.planTime">上门时间</div>
          <div class="v" v-if="order.planTime">{{ order.planTime }}</div>
          <div class="k" v-if="order.finishedAt">完成时间</div>
          <div class="v" v-if="order.finishedAt">{{ order.finishedAt }}</div>
          <div class="k" v-if="order.rejectReason">驳回/取消原因</div>
          <div class="v" v-if="order.rejectReason" style="color: var(--crit)">{{ order.rejectReason }}</div>
        </div>

        <div class="sec">
          <div class="sec-t">故障描述</div>
          <p class="desc">{{ order.description }}</p>
        </div>

        <div class="sec" v-if="order.images?.length">
          <div class="sec-t">现场照片（{{ order.images.length }}）</div>
          <div class="pics">
            <a v-for="img in order.images" :key="img.id" :href="asset(img.url)" target="_blank" rel="noopener">
              <img :src="asset(img.url)" alt="" />
            </a>
          </div>
        </div>

        <OrderSchedulePanel :order="order" :order-id="order.id" @changed="reload" />
        <OrderRecordPanel :order="order" :order-id="order.id" @changed="reload" />
        <OrderAcceptancePanel :order="order" :order-id="order.id" @changed="reload" />
        <OrderEvaluationPanel :order="order" @changed="reload" />
        <OrderChatPanel :order="order" :order-id="order.id" @changed="reload" />

        <div class="sec">
          <div class="sec-t">流转记录</div>
          <div class="tl">
            <div v-for="l in logs" :key="l.id" class="tl-item done">
              <div class="tl-t">
                {{ l.action }}
                <span class="tag tag-ink" style="margin-left: 6px">{{ l.toLabel }}</span>
              </div>
              <div class="tl-d">
                {{ l.operatorName }} · {{ roleLabel(l.operatorRole) }} · {{ l.createdAt }}
                <template v-if="l.remark"><br />{{ l.remark }}</template>
              </div>
            </div>
          </div>
        </div>

        <!-- 指派面板 -->
        <div class="sec assign" v-if="assignMode">
          <div class="sec-t">选择维修人员</div>
          <select v-model.number="pickedRepairer" class="field">
            <option :value="0">请选择</option>
            <option v-for="r in repairers" :key="r.id" :value="r.id">
              {{ r.realName }} · {{ r.deptName || '未分组' }} · 在办 {{ r.wipCount }} 单 · 信誉 {{ r.creditScore }}
            </option>
          </select>

          <!-- 忙闲日历：未来 7 天已确认上门时段 -->
          <div v-if="calendar" class="cal">
            <div class="cal-hd">
              未来 7 天已确认上门 <b>{{ calendar.confirmedCount }}</b> 次 · 在办 {{ calendar.wipCount }} 单
            </div>
            <div v-if="calendar.items.length" class="cal-days">
              <div v-for="d in calendar.items" :key="d.date" class="cal-day">
                <span class="d-date">{{ d.date.slice(5) }}</span>
                <span class="d-cnt">{{ d.count }} 单</span>
                <span v-for="s in d.slots" :key="s.orderId + (s.slotStart || '')" class="d-slot mono">
                  {{ (s.slotStart || '').slice(0, 5) }}–{{ (s.slotEnd || '').slice(0, 5) }}
                </span>
              </div>
            </div>
            <div v-else class="cal-empty">近 7 天无已确认预约，随时可安排</div>
          </div>

          <div class="assign-act">
            <button class="btn btn-sm" @click="assignMode = false">取消</button>
            <button class="btn btn-sm btn-primary" :disabled="!pickedRepairer" @click="doAssign">确认指派</button>
          </div>
        </div>
      </div>

      <div class="drawer-ft">
        <button class="btn" @click="close">关闭</button>
        <span class="spacer" />
        <button
          v-for="a in visibleActions"
          :key="a"
          class="btn"
          :class="actClass(a)"
          :disabled="acting"
          @click="onAction(a)"
        >
          {{ actLabel(a) }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Icon from '../ui/Icon.vue'
import OrderStatusTag from '../ui/OrderStatusTag.vue'
import { resolveAssetUrl } from '../config/env'
import OrderSchedulePanel from './OrderSchedulePanel.vue'
import OrderRecordPanel from './OrderRecordPanel.vue'
import OrderAcceptancePanel from './OrderAcceptancePanel.vue'
import OrderEvaluationPanel from './OrderEvaluationPanel.vue'
import OrderChatPanel from './OrderChatPanel.vue'
import { confirmDialog } from '../ui/dialog'
import { toastErr, toastOk } from '../ui/toast'
import {
  acceptOrder,
  assignOrder,
  cancelOrder,
  confirmOrder,
  getOrder,
  getOrderLogs,
  getRepairerCalendar,
  grabOrder,
  listRepairers,
  rejectOrder,
  returnOrder,
  reviewOrder,
  reworkOrder,
  transferOrder,
  type CalendarVO,
  type OrderDetail,
  type OrderLog,
  type RepairerVO
} from '../api/order'

const props = defineProps<{ orderId: number | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'changed'): void }>()

const open = computed(() => props.orderId !== null)

const order = ref<OrderDetail | null>(null)
const logs = ref<OrderLog[]>([])
const repairers = ref<RepairerVO[]>([])
const assignMode = ref(false)
const pickedRepairer = ref(0)
const calendar = ref<CalendarVO | null>(null)
const acting = ref(false)

/** 选中维修工后拉取其未来 7 天已确认时段，辅助指派决策 */
watch(pickedRepairer, async (id) => {
  calendar.value = null
  if (!id) return
  try {
    calendar.value = await getRepairerCalendar(id, 7)
  } catch {
    calendar.value = null
  }
})

/** 本阶段已实现接口的动作，其余状态流转等下一批 */
const SUPPORTED = ['GRAB', 'ACCEPT', 'RETURN', 'REVIEW_PASS', 'REVIEW_REJECT', 'CANCEL', 'ASSIGN',
  'TRANSFER', 'CONFIRM', 'REWORK']

const visibleActions = computed(() =>
  (order.value?.actions || []).filter((a) => SUPPORTED.includes(a))
)

const asset = (u?: string | null) => resolveAssetUrl(u)

const urgencyText = computed(() => {
  const u = order.value?.urgency
  return u === 3 ? '非常紧急' : u === 2 ? '紧急' : '普通'
})

const LABELS: Record<string, string> = {
  GRAB: '抢单',
  ACCEPT: '确认接单',
  RETURN: '退单',
  REVIEW_PASS: '审核通过',
  REVIEW_REJECT: '驳回',
  CANCEL: '取消工单',
  ASSIGN: '指派',
  TRANSFER: '转派',
  CONFIRM: '确认完成',
  REWORK: '申请返修'
}

const actLabel = (a: string) => LABELS[a] || a
const actClass = (a: string) =>
  a === 'REVIEW_PASS' || a === 'GRAB' || a === 'ACCEPT' || a === 'CONFIRM'
    ? 'btn-primary'
    : a === 'CANCEL' || a === 'REVIEW_REJECT'
      ? 'btn-danger'
      : ''

const ROLE_LABELS: Record<string, string> = {
  STUDENT: '学生',
  REPAIRER: '维修人员',
  MANAGER: '后勤管理员',
  ADMIN: '系统管理员',
  SYSTEM: '系统'
}
const roleLabel = (r: string) => ROLE_LABELS[r] || r

watch(
  () => props.orderId,
  async (id) => {
    if (id === null) {
      order.value = null
      logs.value = []
      calendar.value = null
      return
    }
    assignMode.value = false
    pickedRepairer.value = 0
    await load(id)
  }
)

async function load(id: number) {
  try {
    const [d, l]: any = await Promise.all([getOrder(id), getOrderLogs(id)])
    order.value = d
    logs.value = l || []
  } catch {
    close()
  }
}

/** 预约 / 维修面板操作后刷新抽屉，保持状态、时间轴与按钮同步 */
async function reload() {
  if (props.orderId) {
    await load(props.orderId)
    emit('changed')
  }
}

function close() {
  emit('close')
}

async function onAction(action: string) {
  const o = order.value
  if (!o) return

  if (action === 'ASSIGN' || action === 'TRANSFER') {
    assignMode.value = true
    if (repairers.value.length === 0) {
      try {
        repairers.value = ((await listRepairers()) as any) || []
      } catch {
        /* 拦截器已提示 */
      }
    }
    return
  }

  if (action === 'GRAB') {
    const ok = await confirmDialog({
      title: '抢单',
      message: `确认接下「${o.title}」？接单后请尽快联系报修人约定上门时间。`,
      okText: '确认抢单'
    })
    if (!ok) return
    await run(() => grabOrder(o.id), '抢单成功，请尽快预约上门时间')
    return
  }

  if (action === 'ACCEPT') {
    await run(() => acceptOrder(o.id), '已接单')
    return
  }

  if (action === 'RETURN') {
    const reason = window.prompt('请填写退单原因（退单会扣除 10 点信誉分）')
    if (reason === null) return
    const ok = await confirmDialog({
      title: '确认退单',
      message: '退单后工单会回到抢单池，同时扣除 10 点信誉分。确定继续？',
      okText: '确认退单',
      danger: true
    })
    if (!ok) return
    await run(() => returnOrder(o.id, reason), '已退单，工单回到抢单池')
    return
  }

  if (action === 'REVIEW_PASS') {
    await run(() => reviewOrder(o.id, '审核通过'), '已通过审核，工单进入抢单池')
    return
  }

  if (action === 'REVIEW_REJECT') {
    const reason = window.prompt('请填写驳回原因（学生可见）')
    if (!reason) return
    await run(() => rejectOrder(o.id, reason), '已驳回')
    return
  }

  if (action === 'CONFIRM') {
    const ok = await confirmDialog({
      title: '确认维修完成',
      message: '确认后工单将归档，如仍有问题请走新的报修或质保流程。确定已修好？',
      okText: '确认完成'
    })
    if (!ok) return
    await run(() => confirmOrder(o.id, '报修人确认完成'), '已确认完成')
    return
  }

  if (action === 'REWORK') {
    const reason = window.prompt('请说明返修原因（必填，会记录在流转日志里）')
    if (reason === null) return
    if (!reason.trim()) {
      toastErr('返修原因不能为空')
      return
    }
    const ok = await confirmDialog({
      title: '申请返修',
      message: '工单会退回抢单池，原维修人与预约作废，需要重新派单与预约。确定继续？',
      okText: '确认返修',
      danger: true
    })
    if (!ok) return
    await run(() => reworkOrder(o.id, reason.trim()), '已申请返修，工单退回抢单池')
    return
  }

  if (action === 'CANCEL') {
    const reason = window.prompt('请填写取消原因')
    if (reason === null) return
    const ok = await confirmDialog({
      title: '取消工单',
      message: '取消后不可恢复，确定继续？',
      okText: '确认取消',
      danger: true
    })
    if (!ok) return
    await run(() => cancelOrder(o.id, reason), '工单已取消')
  }
}

async function doAssign() {
  const o = order.value
  if (!o || !pickedRepairer.value) return
  const isTransfer = o.status === 'ASSIGNED'
  await run(
    () =>
      isTransfer
        ? transferOrder(o.id, pickedRepairer.value, '管理员转派')
        : assignOrder(o.id, pickedRepairer.value, '管理员指派'),
    isTransfer ? '已转派' : '已指派'
  )
  assignMode.value = false
  pickedRepairer.value = 0
}

async function run(fn: () => Promise<any>, successMsg: string) {
  acting.value = true
  try {
    await fn()
    toastOk(successMsg)
    if (props.orderId) await load(props.orderId)
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '操作失败')
  } finally {
    acting.value = false
  }
}
</script>

<style scoped>
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(16, 18, 22, 0.42);
  display: flex;
  justify-content: flex-end;
  z-index: 1000;
}
.drawer {
  width: 640px;
  max-width: 96vw;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: -18px 0 48px rgba(16, 18, 24, 0.2);
  animation: slide 0.24s cubic-bezier(0.2, 0.9, 0.3, 1);
}
@keyframes slide {
  from {
    transform: translateX(24px);
    opacity: 0.6;
  }
  to {
    transform: none;
    opacity: 1;
  }
}
.drawer-hd {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 22px 24px 16px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, #fff, #fbfbfd);
}
.drawer-hd h3 {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.3px;
  margin: 0 0 9px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ink-3);
}
.dot-sep {
  color: var(--line);
}
.drawer-bd {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 26px;
}

/* 状态摘要 */
.status-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 18px;
  border-radius: 14px;
  background:
    radial-gradient(280px 120px at 100% 0%, rgba(230, 57, 70, 0.1), transparent 70%),
    linear-gradient(135deg, #fdf1f2, #f8f9fc 70%);
  border: 1px solid #f7dfe1;
  margin-bottom: 18px;
}
.sh-left {
  display: flex;
  align-items: center;
  gap: 11px;
}
.sh-ic {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #f0505c, #e63946);
  color: #fff;
  box-shadow: 0 6px 14px rgba(230, 57, 70, 0.28);
}
.sh-label {
  font-size: 11px;
  color: var(--ink-3);
  letter-spacing: 0.3px;
}
.sh-value {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.4px;
  color: var(--ink);
  margin-top: 1px;
}
.sh-stats {
  display: flex;
  align-items: center;
  gap: 20px;
}
.sh-stats div {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.sh-stats span {
  font-size: 10.5px;
  color: var(--ink-3);
}
.sh-stats b {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}

.kv {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 11px 14px;
  font-size: 13px;
  padding: 16px 18px;
  border-radius: 13px;
  background: #fafbfd;
  border: 1px solid var(--line-2);
}
.kv .k {
  color: var(--ink-3);
}
.kv .v {
  color: var(--ink);
}
.muted {
  color: var(--ink-3);
}
/* 维修人信息卡 */
.repairer-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rp-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  overflow: hidden;
  display: grid;
  place-items: center;
  position: relative;
  background: linear-gradient(140deg, #eef1f6, #e2e6ee);
  color: #b7bdc9;
  flex: none;
}
.rp-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.rp-default {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #f0505c, var(--brand) 55%, #c42532);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}
.rp-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.rp-name {
  font-size: 13px;
  color: var(--ink);
  font-weight: 600;
}
.rp-name .muted {
  font-weight: 400;
  margin-left: 4px;
}
.rp-sub {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 1px;
}
.sec {
  margin-top: 22px;
}
.sec-t {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 9px;
  border-left: 3px solid var(--brand);
  line-height: 1.2;
}
.desc {
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-2);
  background: var(--bg);
  border-radius: 8px;
  padding: 12px 14px;
  white-space: pre-wrap;
}
.pics {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pics a {
  border-radius: 10px;
  overflow: hidden;
  display: block;
  line-height: 0;
}
.pics img {
  width: 92px;
  height: 92px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--line);
  transition: transform 0.2s, box-shadow 0.2s;
}
.pics a:hover img {
  transform: scale(1.04);
  box-shadow: var(--sh-2);
}
.assign .field {
  margin-bottom: 10px;
}
.cal {
  margin-bottom: 12px;
  border: 1px solid var(--line-2);
  border-radius: 11px;
  background: #fafbfd;
  padding: 11px 13px;
}
.cal-hd {
  font-size: 12px;
  color: var(--ink-3);
  margin-bottom: 9px;
}
.cal-hd b {
  color: var(--brand);
}
.cal-days {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.cal-day {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  flex-wrap: wrap;
}
.d-date {
  font-weight: 600;
  color: var(--ink);
  min-width: 42px;
}
.d-cnt {
  color: var(--ink-3);
}
.d-slot {
  font-size: 11px;
  color: var(--accent);
  background: #eef2ff;
  border-radius: 6px;
  padding: 2px 7px;
}
.cal-empty {
  font-size: 12px;
  color: var(--ink-3);
}
.assign-act {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.drawer-ft {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 22px;
  border-top: 1px solid var(--line);
  background: #fcfcfd;
}
.spacer {
  flex: 1;
}
</style>
