<template>
  <div class="s-home">
    <!-- 欢迎区：只保留一个主操作，弱化后台感 -->
    <section class="s-welcome">
      <div class="s-copy">
        <p class="s-date">{{ todayText }} · {{ greeting }}</p>
        <h1>你好，{{ auth.displayName }}</h1>
        <p class="s-line">{{ heroLine }}</p>
        <p class="s-lead">宿舍、教室、网络……遇到问题随时告诉我们，处理进度会同步在这里。</p>

        <div class="s-actions">
          <button class="s-cta" type="button" @click="newOpen = true">提交报修</button>
          <button class="s-link" type="button" @click="router.push('/orders')">
            查看我的报修<span aria-hidden="true">→</span>
          </button>
        </div>
      </div>

      <div v-if="focusOrder && focusMeta" class="s-focus">
        <span class="s-focus-bar" :style="{ background: focusMeta.color }" />
        <div class="s-focus-top">
          <span
            class="s-focus-label"
            :style="{ color: focusMeta.color, background: focusMeta.color + '14' }"
          >
            {{ focusMeta.label }}
          </span>
          <span class="s-focus-time">{{ shortTime(focusOrder.createdAt) }}</span>
        </div>
        <h2 class="s-focus-title">{{ focusOrder.title }}</h2>
        <p class="s-focus-loc">{{ focusOrder.locationPath }}</p>
        <div class="s-focus-foot">
          <span class="s-focus-note">
            {{ focusOrder.assigneeName ? `维修师傅 ${focusOrder.assigneeName}` : focusMeta.note }}
          </span>
          <button class="s-focus-go" type="button" @click="openOrder(focusOrder.id)">
            {{ focusMeta.action }}<span aria-hidden="true">→</span>
          </button>
        </div>
      </div>

      <div v-else class="s-focus s-focus-idle">
        <span class="s-focus-label quiet">一切正常</span>
        <h2 class="s-focus-title">目前没有进行中的报修</h2>
        <p class="s-focus-loc">遇到问题，随时提交，我们会尽快安排。</p>
        <button class="s-focus-go idle" type="button" @click="newOpen = true">
          提交报修<span aria-hidden="true">→</span>
        </button>
      </div>
    </section>

    <!-- 四个状态数字，点击直接筛选 -->
    <section class="s-status">
      <button
        v-for="s in statusCards"
        :key="s.key"
        class="s-card"
        :class="{ alert: s.alert }"
        type="button"
        @click="goStatus(s)"
      >
        <span class="s-bar" :style="{ background: s.color }" />
        <span class="s-label">{{ s.label }}</span>
        <span class="s-num">{{ s.count }}</span>
        <span class="s-hint">{{ s.hint }}</span>
      </button>
    </section>

    <div class="s-lower">
      <section class="s-panel">
        <header class="s-panel-hd">
          <div>
            <h2>我的报修</h2>
            <p>最近提交的报修和最新进度</p>
          </div>
          <button class="s-all" type="button" @click="router.push('/orders')">
            全部<span aria-hidden="true">→</span>
          </button>
        </header>

        <div v-if="loading" class="s-loading">
          <span class="spin" />
          <span>正在加载</span>
        </div>

        <div v-else-if="!recent.length" class="s-blank">
          <p class="s-blank-t">还没有报修记录</p>
          <p class="s-blank-d">遇到问题，提交第一张报修单</p>
          <button class="s-cta sm" type="button" @click="newOpen = true">提交报修</button>
        </div>

        <ul v-else class="s-order-list">
          <li
            v-for="o in recent"
            :key="o.id"
            class="s-order"
            role="button"
            tabindex="0"
            @click="openOrder(o.id)"
            @keyup.enter="openOrder(o.id)"
          >
            <div class="s-order-main">
              <div class="s-order-title-row">
                <span class="s-order-title">{{ o.title }}</span>
                <OrderStatusTag :status="o.status" :label="o.statusLabel" />
              </div>
              <div class="s-order-meta">
                <span>{{ o.locationPath }}</span>
                <span class="s-dot">·</span>
                <span>{{ shortTime(o.createdAt) }}</span>
                <template v-if="o.assigneeName">
                  <span class="s-dot">·</span>
                  <span>{{ o.assigneeName }} 师傅</span>
                </template>
              </div>
              <div v-if="o.status === 'PENDING_CONFIRM'" class="s-order-note">
                师傅已提交完成，请确认验收
              </div>
            </div>
            <span class="s-arrow" aria-hidden="true">→</span>
          </li>
        </ul>
      </section>

      <aside class="s-panel s-guide">
        <header class="s-panel-hd">
          <div>
            <h2>报修流程</h2>
            <p>三步就能搞定</p>
          </div>
        </header>
        <ol class="s-steps">
          <li>
            <span class="s-step-no">1</span>
            <div><b>描述问题</b><p>选好位置，拍下现场照片</p></div>
          </li>
          <li>
            <span class="s-step-no">2</span>
            <div><b>等待上门</b><p>进度和师傅信息都能看到</p></div>
          </li>
          <li>
            <span class="s-step-no">3</span>
            <div><b>确认完成</b><p>满意后验收，并评价服务</p></div>
          </li>
        </ol>
        <p class="s-tip">报修进度有更新时，右上角消息中心会提醒你。</p>
      </aside>
    </div>

    <NewOrderModal :open="newOpen" @close="newOpen = false" @created="onCreated" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import OrderStatusTag from '../../ui/OrderStatusTag.vue'
import NewOrderModal from '../NewOrderModal.vue'
import { useAuthStore } from '../../stores/auth'
import { pageOrders, type OrderVO } from '../../api/order'
import { getStatsOverview, type StatsOverview } from '../../api/stats'

const auth = useAuthStore()
const router = useRouter()

const rows = ref<OrderVO[]>([])
const overview = ref<StatsOverview | null>(null)
const loading = ref(true)
const newOpen = ref(false)

const counts = computed<Record<string, number>>(() => overview.value?.statusCounts || {})
const countOf = (statuses: string[]) =>
  statuses.reduce((n, s) => n + (Number(counts.value[s]) || 0), 0)

const waitingCount = computed(() => countOf(['PENDING_REVIEW', 'PENDING', 'ASSIGNED']))
const activeCount = computed(() => countOf(['ACCEPTED', 'PROCESSING', 'SUSPENDED']))
const confirmCount = computed(() => countOf(['PENDING_CONFIRM']))
const doneCount = computed(() => countOf(['COMPLETED']))
const openCount = computed(() => waitingCount.value + activeCount.value + confirmCount.value)

const statusCards = computed(() => [
  {
    key: 'waiting',
    label: '待处理',
    count: waitingCount.value,
    hint: '等待受理或分配',
    color: '#e63946',
    to: '/orders?status=PENDING_REVIEW,PENDING,ASSIGNED',
    alert: false
  },
  {
    key: 'active',
    label: '处理中',
    count: activeCount.value,
    hint: '师傅正在跟进',
    color: '#4f6ef7',
    to: '/orders?status=ACCEPTED,PROCESSING,SUSPENDED',
    alert: false
  },
  {
    key: 'confirm',
    label: '待确认',
    count: confirmCount.value,
    hint: '完成后等你验收',
    color: '#f59e0b',
    to: '/orders?status=PENDING_CONFIRM',
    alert: confirmCount.value > 0
  },
  {
    key: 'done',
    label: '已完成',
    count: doneCount.value,
    hint: '历史完成记录',
    color: '#12a150',
    to: '/orders?status=COMPLETED',
    alert: false
  }
])

const heroLine = computed(() => {
  if (confirmCount.value > 0) return `有 ${confirmCount.value} 张报修等待你确认`
  if (openCount.value > 0) return `有 ${openCount.value} 张报修正在跟进`
  return '有需要，随时报修'
})

/* 焦点卡片：优先展示最需要学生处理 / 关注的那一张 */
interface FocusMeta {
  label: string
  note: string
  action: string
  color: string
}

const FOCUS_META: Record<string, FocusMeta> = {
  PENDING_CONFIRM: { label: '待你确认', note: '维修已完成，请确认验收', action: '去确认', color: '#f59e0b' },
  PROCESSING: { label: '正在维修', note: '师傅正在处理中', action: '查看进度', color: '#4f6ef7' },
  ACCEPTED: { label: '师傅已接单', note: '等待上门处理', action: '查看进度', color: '#4f6ef7' },
  SUSPENDED: { label: '维修暂停', note: '等待配件或进一步安排', action: '查看进度', color: '#dc3545' },
  ASSIGNED: { label: '已分配师傅', note: '师傅准备上门', action: '查看进度', color: '#8a90a0' },
  PENDING: { label: '等待受理', note: '后勤正在安排处理', action: '查看进度', color: '#e63946' },
  PENDING_REVIEW: { label: '已提交', note: '等待后勤审核', action: '查看进度', color: '#f59e0b' }
}

const FOCUS_RANK: Record<string, number> = {
  PENDING_CONFIRM: 0,
  PROCESSING: 1,
  ACCEPTED: 2,
  SUSPENDED: 3,
  ASSIGNED: 4,
  PENDING: 5,
  PENDING_REVIEW: 6
}

const focusOrder = computed(() => {
  const list = rows.value.filter((o) => FOCUS_RANK[o.status] !== undefined)
  return list.sort((a, b) => FOCUS_RANK[a.status] - FOCUS_RANK[b.status])[0] || null
})
const focusMeta = computed(() => (focusOrder.value ? FOCUS_META[focusOrder.value.status] : null))
const recent = computed(() => rows.value.slice(0, 6))

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

function shortTime(s?: string) {
  if (!s) return '—'
  const date = s.slice(0, 10)
  const time = s.slice(11, 16)
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  const today = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
  if (date === today) return `今天 ${time}`
  return `${date.slice(5)} ${time}`
}

function goStatus(s: { to: string }) {
  const [path, query] = s.to.split('?')
  router.push({ path, query: query ? Object.fromEntries(new URLSearchParams(query)) : {} })
}

function openOrder(id: number) {
  router.push({ path: '/orders', query: { id: String(id) } })
}

async function load() {
  loading.value = true
  const [ovRes, pageRes] = await Promise.allSettled([
    getStatsOverview(),
    pageOrders({ page: 1, size: 8 })
  ])
  overview.value = ovRes.status === 'fulfilled' ? ovRes.value : null
  rows.value = pageRes.status === 'fulfilled' ? (pageRes.value as any)?.list || [] : []
  loading.value = false
}

function onCreated(id: number) {
  newOpen.value = false
  load()
  if (id) openOrder(id)
}

onMounted(load)
</script>

<style scoped>
.s-home {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ------------------------------------------------ 欢迎区 */
.s-welcome {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  padding: 34px 36px;
  background:
    radial-gradient(540px 320px at 94% -12%, rgba(255, 122, 89, 0.18), transparent 64%),
    radial-gradient(420px 260px at 2% 120%, rgba(230, 57, 70, 0.07), transparent 60%),
    linear-gradient(135deg, #fff8f5 0%, #fff 56%, #fff6f4 100%);
  border: 1px solid #f6e4de;
  box-shadow: 0 18px 44px rgba(230, 57, 70, 0.06);
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(292px, 0.65fr);
  gap: 30px;
  align-items: center;
}
.s-welcome::after {
  content: "";
  position: absolute;
  right: -110px;
  top: -140px;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  border: 1px solid rgba(230, 57, 70, 0.12);
  pointer-events: none;
}
.s-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}
.s-date {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: var(--brand);
}
.s-copy h1 {
  margin: 12px 0 6px;
  font-size: 31px;
  font-weight: 800;
  letter-spacing: -0.8px;
  line-height: 1.25;
}
.s-line {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--ink);
}
.s-lead {
  margin-top: 9px;
  max-width: 470px;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--ink-3);
}
.s-actions {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 24px;
  flex-wrap: wrap;
}
.s-cta {
  height: 48px;
  padding: 0 30px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ff7a59, var(--brand));
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 12px 26px rgba(230, 57, 70, 0.26);
  transition: transform 0.14s, box-shadow 0.16s, filter 0.16s;
}
.s-cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(230, 57, 70, 0.32);
  filter: brightness(1.02);
}
.s-cta:active {
  transform: translateY(0);
}
.s-cta.sm {
  height: 40px;
  padding: 0 22px;
  border-radius: 12px;
  font-size: 13.5px;
}
.s-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 2px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink-2);
  border-bottom: 1px solid transparent;
  transition: color 0.14s, border-color 0.14s;
}
.s-link:hover {
  color: var(--brand);
  border-color: rgba(230, 57, 70, 0.28);
}

/* 右侧焦点卡 */
.s-focus {
  position: relative;
  z-index: 1;
  min-width: 0;
  padding: 20px 20px 18px 24px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #eee6e2;
  box-shadow: 0 12px 28px rgba(20, 24, 40, 0.06);
}
.s-focus-bar {
  position: absolute;
  left: 0;
  top: 18px;
  bottom: 18px;
  width: 4px;
  border-radius: 0 4px 4px 0;
}
.s-focus-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.s-focus-label {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 7px;
  font-size: 11.5px;
  font-weight: 700;
}
.s-focus-label.quiet {
  background: #f1f3f7;
  color: var(--ink-3);
}
.s-focus-time {
  font-size: 11.5px;
  color: var(--ink-3);
}
.s-focus-title {
  margin-top: 12px;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.2px;
  line-height: 1.45;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.s-focus-loc {
  margin-top: 5px;
  font-size: 12px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.s-focus-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 16px;
  padding-top: 13px;
  border-top: 1px solid var(--line-2);
}
.s-focus-note {
  min-width: 0;
  font-size: 12px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.s-focus-go {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--brand);
  transition: gap 0.14s;
}
.s-focus-go:hover {
  gap: 8px;
}
.s-focus-idle .s-focus-title {
  margin-top: 12px;
}
.s-focus-idle .s-focus-go.idle {
  margin-top: 16px;
}

/* ------------------------------------------------ 状态数字 */
.s-status {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.s-card {
  position: relative;
  overflow: hidden;
  text-align: left;
  padding: 18px 20px 17px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(230, 232, 239, 0.9);
  box-shadow: var(--sh-1);
  transition: transform 0.16s, box-shadow 0.16s, border-color 0.16s;
}
.s-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sh-2);
  border-color: #e0e3ec;
}
.s-card.alert {
  border-color: #f6d9a0;
  box-shadow: 0 10px 24px rgba(245, 158, 11, 0.1);
}
.s-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}
.s-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}
.s-num {
  display: block;
  margin-top: 6px;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -1.2px;
  line-height: 1.1;
  color: var(--ink);
}
.s-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-3);
}

/* ------------------------------------------------ 下半区 */
.s-lower {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.72fr);
  gap: 18px;
  align-items: start;
}
.s-panel {
  background: #fff;
  border: 1px solid rgba(230, 232, 239, 0.9);
  border-radius: 18px;
  box-shadow: var(--sh-1);
  overflow: hidden;
}
.s-panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 16px;
}
.s-panel-hd h2 {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.2px;
}
.s-panel-hd p {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ink-3);
}
.s-all {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-3);
  transition: color 0.14s, gap 0.14s;
}
.s-all:hover {
  color: var(--brand);
  gap: 7px;
}
.s-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 52px 20px;
  font-size: 13px;
  color: var(--ink-3);
}
.s-blank {
  padding: 44px 20px 48px;
  text-align: center;
}
.s-blank-t {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}
.s-blank-d {
  margin: 4px 0 16px;
  font-size: 12.5px;
  color: var(--ink-3);
}
.s-order-list {
  border-top: 1px solid var(--line-2);
}
.s-order {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--line-2);
  cursor: pointer;
  transition: background 0.14s;
}
.s-order:last-child {
  border-bottom: 0;
}
.s-order:hover {
  background: #fffaf9;
}
.s-order:focus-visible {
  outline: 2px solid rgba(230, 57, 70, 0.35);
  outline-offset: -2px;
}
.s-order-main {
  flex: 1;
  min-width: 0;
}
.s-order-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.s-order-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.s-order-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}
.s-dot {
  color: #c4c9d4;
}
.s-order-note {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #a16207;
}
.s-arrow {
  flex: none;
  font-size: 16px;
  color: var(--ink-3);
  transition: transform 0.14s, color 0.14s;
}
.s-order:hover .s-arrow {
  color: var(--brand);
  transform: translateX(3px);
}

/* 流程提示 */
.s-steps {
  padding: 2px 22px 4px;
}
.s-steps li {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--line-2);
}
.s-steps li:last-child {
  border-bottom: 0;
}
.s-step-no {
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--brand-l);
  color: var(--brand);
  font-size: 12px;
  font-weight: 800;
}
.s-steps b {
  font-size: 13.5px;
  font-weight: 600;
}
.s-steps p {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--ink-3);
}
.s-tip {
  margin: 10px 22px 22px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #faf7f4;
  font-size: 12px;
  line-height: 1.7;
  color: #8a6a53;
}

/* ------------------------------------------------ 响应式 */
@media (max-width: 1080px) {
  .s-welcome {
    grid-template-columns: 1fr;
    gap: 22px;
  }
  .s-focus {
    max-width: none;
  }
  .s-lower {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 760px) {
  .s-welcome {
    padding: 26px 22px;
    border-radius: 20px;
  }
  .s-copy h1 {
    font-size: 26px;
  }
  .s-line {
    font-size: 18px;
  }
  .s-lead {
    font-size: 13px;
  }
  .s-actions {
    gap: 12px;
    margin-top: 20px;
  }
  .s-cta {
    height: 44px;
    padding: 0 24px;
  }
  .s-status {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }
  .s-card {
    padding: 16px 15px 15px;
    border-radius: 15px;
  }
  .s-num {
    font-size: 30px;
  }
  .s-hint {
    font-size: 11.5px;
  }
  .s-panel-hd {
    padding: 16px 18px 12px;
  }
  .s-order {
    padding: 14px 18px;
  }
  .s-order-title {
    white-space: normal;
  }
  .s-steps {
    padding: 0 18px 2px;
  }
  .s-tip {
    margin: 8px 18px 18px;
  }
}
</style>
