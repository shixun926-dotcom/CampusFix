<template>
  <div class="r-home">
    <!-- 欢迎区：任务数量与两个主要入口一目了然 -->
    <section class="r-welcome">
      <div class="r-copy">
        <p class="r-date">{{ todayText }} · {{ greeting }}</p>
        <h1>你好，{{ auth.displayName }}</h1>
        <p class="r-line">{{ heroLine }}</p>
        <p class="r-lead">接单、上门、提交结果，今天的任务都在这里。</p>

        <div class="r-actions">
          <button class="r-cta" type="button" @click="router.push('/orders')">
            我的任务<em v-if="openCount">{{ openCount }}</em>
          </button>
          <button class="r-cta outline" type="button" @click="router.push('/orders?tab=pool')">
            抢单池<em v-if="poolTotal">{{ poolTotal }}</em>
          </button>
        </div>
      </div>

      <div class="r-today">
        <span class="r-today-label">今日已完成</span>
        <strong class="r-today-num">{{ doneToday }}<small>张</small></strong>
        <div class="r-today-meta">
          <div><span>本周完成</span><b>{{ doneWeek }} 张</b></div>
          <div><span>待学生确认</span><b>{{ confirmCount }} 张</b></div>
        </div>
      </div>
    </section>

    <!-- 关键数量 -->
    <section class="r-status">
      <button
        v-for="s in statusCards"
        :key="s.key"
        class="r-card"
        :class="{ alert: s.alert }"
        type="button"
        @click="goStatus(s)"
      >
        <span class="r-bar" :style="{ background: s.color }" />
        <span class="r-label">{{ s.label }}</span>
        <span class="r-num">{{ s.count }}</span>
        <span class="r-hint">{{ s.hint }}</span>
      </button>
    </section>

    <div class="r-lower">
      <section class="r-panel">
        <header class="r-panel-hd">
          <div>
            <h2>我的任务</h2>
            <p>按处理顺序为你排好</p>
          </div>
          <button class="r-all" type="button" @click="router.push('/orders')">
            全部<span aria-hidden="true">→</span>
          </button>
        </header>

        <div v-if="loading" class="r-loading">
          <span class="spin" />
          <span>正在加载</span>
        </div>

        <div v-else-if="!tasks.length" class="r-blank">
          <p class="r-blank-t">目前没有待处理任务</p>
          <p class="r-blank-d">去抢单池看看，或者稍后再来</p>
          <button class="r-cta sm" type="button" @click="router.push('/orders?tab=pool')">
            去抢单池
          </button>
        </div>

        <ul v-else class="r-task-list">
          <li
            v-for="t in tasks"
            :key="t.id"
            class="r-task"
            role="button"
            tabindex="0"
            @click="openOrder(t.id)"
            @keyup.enter="openOrder(t.id)"
          >
            <span class="r-task-bar" :style="{ background: metaOf(t.status).color }" />
            <div class="r-task-main">
              <div class="r-task-title-row">
                <span class="r-task-title">{{ t.title }}</span>
                <OrderStatusTag :status="t.status" :label="t.statusLabel" />
              </div>
              <div class="r-task-meta">
                <span>{{ t.locationPath }}</span>
                <span class="r-dot">·</span>
                <span>{{ t.categoryName || '未分类' }}</span>
                <span class="r-dot">·</span>
                <span>{{ shortTime(t.createdAt) }}</span>
              </div>
              <div v-if="t.urgency >= 3" class="r-task-urgent">紧急工单，请优先处理</div>
            </div>
            <span class="r-task-act" :style="{ color: metaOf(t.status).color }">
              {{ metaOf(t.status).action }}<span aria-hidden="true">→</span>
            </span>
          </li>
        </ul>
      </section>

      <aside class="r-panel r-pool">
        <header class="r-panel-hd">
          <div>
            <h2>可抢工单</h2>
            <p>新提交、还没人接的报修</p>
          </div>
          <span v-if="poolTotal" class="r-pool-count">{{ poolTotal }} 张</span>
        </header>

        <div v-if="loading" class="r-loading slim">
          <span class="spin" />
          <span>正在加载</span>
        </div>

        <div v-else-if="!pool.length" class="r-pool-empty">
          <p>抢单池暂时空了</p>
          <span>有新的报修会出现在这里</span>
        </div>

        <ul v-else class="r-pool-list">
          <li
            v-for="o in pool"
            :key="o.id"
            class="r-pool-item"
            role="button"
            tabindex="0"
            @click="openOrder(o.id)"
            @keyup.enter="openOrder(o.id)"
          >
            <div class="r-pool-title-row">
              <span class="r-pool-title">{{ o.title }}</span>
              <span v-if="o.urgency >= 3" class="r-urgent">紧急</span>
            </div>
            <p class="r-pool-meta">{{ o.locationPath }}</p>
            <div class="r-pool-foot">
              <span>{{ shortTime(o.createdAt) }}</span>
              <span class="r-pool-go">抢单<span aria-hidden="true">→</span></span>
            </div>
          </li>
        </ul>

        <button class="r-pool-all" type="button" @click="router.push('/orders?tab=pool')">
          进入抢单池<span aria-hidden="true">→</span>
        </button>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import OrderStatusTag from '../../ui/OrderStatusTag.vue'
import { useAuthStore } from '../../stores/auth'
import { pageOrders, pagePool, type OrderVO } from '../../api/order'
import { getStatsOverview, type StatsOverview } from '../../api/stats'

const auth = useAuthStore()
const router = useRouter()

const rows = ref<OrderVO[]>([])
const pool = ref<OrderVO[]>([])
const poolTotal = ref(0)
const overview = ref<StatsOverview | null>(null)
const loading = ref(true)

const counts = computed<Record<string, number>>(() => overview.value?.statusCounts || {})
const countOf = (statuses: string[]) =>
  statuses.reduce((n, s) => n + (Number(counts.value[s]) || 0), 0)

const assignedCount = computed(() => countOf(['ASSIGNED']))
const activeCount = computed(() => countOf(['ACCEPTED', 'PROCESSING', 'SUSPENDED']))
const confirmCount = computed(() => countOf(['PENDING_CONFIRM']))
const openCount = computed(() => assignedCount.value + activeCount.value + confirmCount.value)
const doneToday = computed(() => overview.value?.completed?.today || 0)
const doneWeek = computed(() => overview.value?.completed?.week || 0)

const heroLine = computed(() => {
  if (openCount.value > 0) return `今天有 ${openCount.value} 个任务要跟进`
  if (poolTotal.value > 0) return `抢单池有 ${poolTotal.value} 个新任务`
  return '今天暂时没有待处理任务'
})

const statusCards = computed(() => [
  {
    key: 'assigned',
    label: '待接单',
    count: assignedCount.value,
    hint: '请尽快确认',
    color: '#e63946',
    to: '/orders?status=ASSIGNED',
    alert: assignedCount.value > 0
  },
  {
    key: 'active',
    label: '进行中',
    count: activeCount.value,
    hint: '正在处理的任务',
    color: '#4f6ef7',
    to: '/orders?status=ACCEPTED,PROCESSING,SUSPENDED',
    alert: false
  },
  {
    key: 'confirm',
    label: '待验收',
    count: confirmCount.value,
    hint: '等待学生确认',
    color: '#f59e0b',
    to: '/orders?status=PENDING_CONFIRM',
    alert: false
  },
  {
    key: 'pool',
    label: '可抢单',
    count: poolTotal.value,
    hint: '新的报修任务',
    color: '#0ea5a4',
    to: '/orders?tab=pool',
    alert: false
  }
])

const STATUS_META: Record<string, { color: string; action: string }> = {
  ASSIGNED: { color: '#e63946', action: '去接单' },
  ACCEPTED: { color: '#4f6ef7', action: '去处理' },
  PROCESSING: { color: '#4f6ef7', action: '继续处理' },
  SUSPENDED: { color: '#dc3545', action: '继续处理' },
  PENDING_CONFIRM: { color: '#f59e0b', action: '查看' }
}
const FALLBACK_META = { color: '#8a90a0', action: '查看' }
const metaOf = (status: string) => STATUS_META[status] || FALLBACK_META

const TASK_RANK: Record<string, number> = {
  ASSIGNED: 0,
  ACCEPTED: 1,
  PROCESSING: 2,
  SUSPENDED: 3,
  PENDING_CONFIRM: 4
}

const tasks = computed(() =>
  [...rows.value]
    .sort(
      (a, b) =>
        (TASK_RANK[a.status] ?? 9) - (TASK_RANK[b.status] ?? 9) ||
        (b.urgency || 0) - (a.urgency || 0) ||
        (a.createdAt || '').localeCompare(b.createdAt || '')
    )
    .slice(0, 6)
)

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
  const [ovRes, taskRes, poolRes] = await Promise.allSettled([
    getStatsOverview(),
    pageOrders({
      page: 1,
      size: 30,
      status: 'ASSIGNED,ACCEPTED,PROCESSING,SUSPENDED,PENDING_CONFIRM'
    }),
    pagePool({ page: 1, size: 4 })
  ])
  overview.value = ovRes.status === 'fulfilled' ? ovRes.value : null
  rows.value = taskRes.status === 'fulfilled' ? (taskRes.value as any)?.list || [] : []
  if (poolRes.status === 'fulfilled') {
    const data = poolRes.value as any
    pool.value = data?.list || []
    poolTotal.value = Number(data?.total ?? pool.value.length) || 0
  } else {
    pool.value = []
    poolTotal.value = 0
  }
  loading.value = false
}

onMounted(load)
</script>

<style scoped>
.r-home {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ------------------------------------------------ 欢迎区 */
.r-welcome {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  padding: 34px 36px;
  background:
    radial-gradient(540px 320px at 94% -12%, rgba(79, 110, 247, 0.15), transparent 64%),
    radial-gradient(420px 260px at 2% 120%, rgba(14, 165, 164, 0.08), transparent 60%),
    linear-gradient(135deg, #f6f8ff 0%, #fff 56%, #f4fbfa 100%);
  border: 1px solid #e1e7f5;
  box-shadow: 0 18px 44px rgba(79, 110, 247, 0.06);
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(292px, 0.65fr);
  gap: 30px;
  align-items: center;
}
.r-welcome::after {
  content: "";
  position: absolute;
  right: -110px;
  top: -140px;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  border: 1px solid rgba(79, 110, 247, 0.12);
  pointer-events: none;
}
.r-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}
.r-date {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: var(--accent);
}
.r-copy h1 {
  margin: 12px 0 6px;
  font-size: 31px;
  font-weight: 800;
  letter-spacing: -0.8px;
  line-height: 1.25;
}
.r-line {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--ink);
}
.r-lead {
  margin-top: 9px;
  max-width: 470px;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--ink-3);
}
.r-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  flex-wrap: wrap;
}
.r-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 48px;
  padding: 0 28px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6b84ff, var(--accent));
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 12px 26px rgba(79, 110, 247, 0.26);
  transition: transform 0.14s, box-shadow 0.16s, background 0.16s;
}
.r-cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(79, 110, 247, 0.32);
}
.r-cta:active {
  transform: translateY(0);
}
.r-cta em {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.22);
  font-style: normal;
  font-size: 12px;
  font-weight: 700;
}
.r-cta.outline {
  background: #fff;
  color: var(--accent);
  border: 1px solid #cdd6f7;
  box-shadow: none;
}
.r-cta.outline em {
  background: var(--accent-l);
  color: var(--accent);
}
.r-cta.outline:hover {
  background: #f7f9ff;
  box-shadow: 0 8px 18px rgba(79, 110, 247, 0.1);
}
.r-cta.sm {
  height: 40px;
  padding: 0 22px;
  border-radius: 12px;
  font-size: 13.5px;
}

/* 今日完成卡 */
.r-today {
  position: relative;
  z-index: 1;
  padding: 20px 22px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #e2e7f3;
  box-shadow: 0 12px 28px rgba(20, 24, 40, 0.06);
}
.r-today-label {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-3);
}
.r-today-num {
  display: block;
  margin-top: 4px;
  font-size: 42px;
  line-height: 1.1;
  font-weight: 800;
  letter-spacing: -1.4px;
  color: var(--accent);
}
.r-today-num small {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--ink-3);
}
.r-today-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line-2);
}
.r-today-meta span {
  display: block;
  font-size: 11.5px;
  color: var(--ink-3);
}
.r-today-meta b {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ink-2);
}

/* ------------------------------------------------ 关键数量 */
.r-status {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.r-card {
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
.r-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sh-2);
  border-color: #e0e3ec;
}
.r-card.alert {
  border-color: #f2c4c8;
  box-shadow: 0 10px 24px rgba(230, 57, 70, 0.1);
}
.r-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}
.r-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}
.r-num {
  display: block;
  margin-top: 6px;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -1.2px;
  line-height: 1.1;
  color: var(--ink);
}
.r-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-3);
}

/* ------------------------------------------------ 下半区 */
.r-lower {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.75fr);
  gap: 18px;
  align-items: start;
}
.r-panel {
  background: #fff;
  border: 1px solid rgba(230, 232, 239, 0.9);
  border-radius: 18px;
  box-shadow: var(--sh-1);
  overflow: hidden;
}
.r-panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 16px;
}
.r-panel-hd h2 {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.2px;
}
.r-panel-hd p {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ink-3);
}
.r-all {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-3);
  transition: color 0.14s, gap 0.14s;
}
.r-all:hover {
  color: var(--accent);
  gap: 7px;
}
.r-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 52px 20px;
  font-size: 13px;
  color: var(--ink-3);
}
.r-loading.slim {
  padding: 36px 20px;
}
.r-blank {
  padding: 44px 20px 48px;
  text-align: center;
}
.r-blank-t {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}
.r-blank-d {
  margin: 4px 0 16px;
  font-size: 12.5px;
  color: var(--ink-3);
}

/* 任务列表 */
.r-task-list {
  border-top: 1px solid var(--line-2);
}
.r-task {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 22px 16px 24px;
  border-bottom: 1px solid var(--line-2);
  cursor: pointer;
  transition: background 0.14s;
}
.r-task:last-child {
  border-bottom: 0;
}
.r-task:hover {
  background: #f8faff;
}
.r-task:focus-visible {
  outline: 2px solid rgba(79, 110, 247, 0.35);
  outline-offset: -2px;
}
.r-task-bar {
  position: absolute;
  left: 0;
  top: 16px;
  bottom: 16px;
  width: 3px;
  border-radius: 0 3px 3px 0;
}
.r-task-main {
  flex: 1;
  min-width: 0;
}
.r-task-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.r-task-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-task-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}
.r-dot {
  color: #c4c9d4;
}
.r-task-urgent {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--brand);
}
.r-task-act {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12.5px;
  font-weight: 700;
  white-space: nowrap;
}

/* 抢单池 */
.r-pool-count {
  font-size: 12px;
  font-weight: 700;
  color: var(--teal);
  background: #e7f7f6;
  padding: 4px 9px;
  border-radius: 8px;
}
.r-pool-empty {
  padding: 38px 22px 30px;
  text-align: center;
}
.r-pool-empty p {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink-2);
}
.r-pool-empty span {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}
.r-pool-list {
  border-top: 1px solid var(--line-2);
}
.r-pool-item {
  padding: 14px 22px;
  border-bottom: 1px solid var(--line-2);
  cursor: pointer;
  transition: background 0.14s;
}
.r-pool-item:hover {
  background: #f8fcfc;
}
.r-pool-item:focus-visible {
  outline: 2px solid rgba(14, 165, 164, 0.3);
  outline-offset: -2px;
}
.r-pool-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.r-pool-title {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-urgent {
  flex: none;
  padding: 2px 7px;
  border-radius: 6px;
  background: var(--brand-l);
  color: var(--brand);
  font-size: 10.5px;
  font-weight: 700;
}
.r-pool-meta {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-pool-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--ink-3);
}
.r-pool-go {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-weight: 700;
  color: var(--teal);
  transition: gap 0.14s;
}
.r-pool-item:hover .r-pool-go {
  gap: 7px;
}
.r-pool-all {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  padding: 13px;
  border-top: 1px solid var(--line-2);
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-2);
  background: #fcfdfd;
  transition: background 0.14s, color 0.14s, gap 0.14s;
}
.r-pool-all:hover {
  background: #f2fbfa;
  color: var(--teal);
  gap: 8px;
}

/* ------------------------------------------------ 响应式 */
@media (max-width: 1080px) {
  .r-welcome {
    grid-template-columns: 1fr;
    gap: 22px;
  }
  .r-today {
    max-width: 560px;
  }
  .r-lower {
    grid-template-columns: 1fr;
  }
}
/* 平板宽度：今日完成卡改为横向条，避免堆叠后出现大片空白 */
@media (min-width: 761px) and (max-width: 1080px) {
  .r-today {
    max-width: none;
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px 20px;
  }
  .r-today-label {
    flex: none;
  }
  .r-today-num {
    margin-top: 0;
    font-size: 36px;
    flex: none;
  }
  .r-today-meta {
    flex: 1;
    grid-template-columns: 1fr 1fr;
    max-width: 320px;
    margin: 0 0 0 auto;
    padding: 0 0 0 20px;
    border-top: 0;
    border-left: 1px solid var(--line-2);
  }
}
@media (max-width: 760px) {
  .r-welcome {
    padding: 26px 22px;
    border-radius: 20px;
  }
  .r-copy h1 {
    font-size: 26px;
  }
  .r-line {
    font-size: 18px;
  }
  .r-lead {
    font-size: 13px;
  }
  .r-actions {
    margin-top: 20px;
  }
  .r-cta {
    height: 44px;
    padding: 0 22px;
  }
  .r-status {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }
  .r-card {
    padding: 16px 15px 15px;
    border-radius: 15px;
  }
  .r-num {
    font-size: 30px;
  }
  .r-hint {
    font-size: 11.5px;
  }
  .r-panel-hd {
    padding: 16px 18px 12px;
  }
  .r-task {
    padding: 14px 18px 14px 20px;
  }
  .r-task-act {
    font-size: 12px;
  }
  .r-pool-item {
    padding: 13px 18px;
  }
}
</style>
