<template>
  <StudentHome v-if="isStudentRole" />
  <RepairerHome v-else-if="isRepairerRole" />
  <div v-else class="dash">
    <!-- ======================= 高频故障预警 ======================= -->
    <section v-if="alerts.length" class="alert-banner">
      <span class="ab-ic"><Icon name="alert" :size="17" /></span>
      <div class="ab-body">
        <b>高频故障预警</b>
        <span v-for="a in alerts" :key="a.id" class="ab-item">{{ a.content }}</span>
      </div>
      <button class="ab-btn" type="button" @click="dismissAlert(alerts[0])">知道了</button>
    </section>

    <!-- ======================= 欢迎横幅 ======================= -->
    <section class="hero">
      <div class="hero-main">
        <div class="hero-eyebrow">
          <span class="pulse" />
          <span class="eyebrow-date">{{ todayText }} · </span>{{ greeting }}
        </div>
        <h1 class="hero-title">
          你好，{{ auth.displayName }}
          <em>{{ heroLine }}</em>
        </h1>
        <p class="hero-sub">
          从报修到验收，每一步都有迹可循。在这里查看与你相关的工单进展，以及平台的整体运行情况。
        </p>

        <div class="hero-actions">
          <button class="btn hero-btn" @click="newOpen = true">
            <Icon name="plus" :size="16" />提交报修
          </button>
          <button class="btn hero-btn-ghost" @click="router.push('/orders')">
            查看全部工单<Icon name="arrowRight" :size="15" />
          </button>
          <span v-if="loading" class="hero-sync"><span class="spin" />同步中</span>
        </div>

        <div class="hero-meta">
          <span><Icon name="shield" :size="14" />{{ roleText }}</span>
          <span><Icon name="calendar" :size="14" />{{ todayText }}</span>
        </div>
      </div>

      <div class="hero-art" aria-hidden="true">
        <svg class="art-svg" viewBox="0 0 380 240" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="cf-warm" x1="60" y1="60" x2="330" y2="220" gradientUnits="userSpaceOnUse">
              <stop stop-color="#cbd5e1" />
              <stop offset="1" stop-color="#64748b" />
            </linearGradient>
            <linearGradient id="cf-cool" x1="80" y1="40" x2="300" y2="220" gradientUnits="userSpaceOnUse">
              <stop stop-color="#e2e8f0" />
              <stop offset="1" stop-color="#475569" stop-opacity=".2" />
            </linearGradient>
          </defs>

          <circle cx="196" cy="118" r="108" fill="url(#cf-warm)" opacity=".16" />
          <circle cx="300" cy="62" r="42" stroke="#fff" stroke-opacity=".18" stroke-dasharray="3 7" />
          <circle cx="86" cy="176" r="4" fill="#cbd5e1" opacity=".8" />
          <circle cx="316" cy="168" r="3" fill="#e2e8f0" opacity=".9" />

          <!-- 校园建筑线稿 -->
          <path d="M52 202h276" stroke="#fff" stroke-opacity=".26" stroke-linecap="round" />
          <rect x="92" y="108" width="62" height="94" rx="9" fill="#fff" fill-opacity=".11" stroke="#fff" stroke-opacity=".32" />
          <rect x="164" y="70" width="76" height="132" rx="10" fill="#fff" fill-opacity=".14" stroke="#fff" stroke-opacity=".38" />
          <rect x="250" y="126" width="52" height="76" rx="8" fill="#fff" fill-opacity=".08" stroke="#fff" stroke-opacity=".28" />

          <g fill="#fff" fill-opacity=".44">
            <rect x="106" y="124" width="10" height="8" rx="2" />
            <rect x="126" y="124" width="10" height="8" rx="2" />
            <rect x="106" y="142" width="10" height="8" rx="2" />
            <rect x="126" y="142" width="10" height="8" rx="2" />
            <rect x="181" y="90" width="12" height="9" rx="2.5" />
            <rect x="205" y="90" width="12" height="9" rx="2.5" />
            <rect x="181" y="112" width="12" height="9" rx="2.5" />
            <rect x="205" y="112" width="12" height="9" rx="2.5" />
            <rect x="264" y="142" width="9" height="7" rx="2" />
            <rect x="281" y="142" width="9" height="7" rx="2" />
          </g>

          <!-- 维修徽章 -->
          <circle cx="280" cy="74" r="28" fill="url(#cf-warm)" />
          <path d="M272.5 78.5l-4.5 4.5m4.5-4.5 2.2 2.2m-2.2-2.2 6.3-6.3a6 6 0 0 1 7.6-7.6l-3.6 3.6 1.8 1.8 3.6-3.6a6 6 0 0 1-7.6 7.6l-6.3 6.3" stroke="#fff" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" />
          <circle cx="122" cy="132" r="14" fill="#475569" stroke="#94a3b8" stroke-opacity=".5" />
          <path d="M116.5 132.4l3.6 3.6 7-7.2" stroke="#fff" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round" />

          <!-- 连接弧线 -->
          <path d="M138 132c26-20 48-26 74-10" stroke="url(#cf-cool)" stroke-width="1.6" stroke-dasharray="4 6" />
          <path d="M252 74c-18 2-30 10-36 22" stroke="#fff" stroke-opacity=".24" stroke-width="1.4" stroke-dasharray="3 5" />
        </svg>

        <div class="float-card fc-1 clickable" role="button" @click="goCard('/orders?status=COMPLETED')">
          <span class="fc-ic ok"><Icon name="check" :size="15" /></span>
          <div><b>{{ stats.done }}</b><span>已完成工单</span></div>
        </div>
        <div class="float-card fc-2 clickable" role="button" @click="goCard(`/orders?status=${STATUS_WAITING}`)">
          <span class="fc-ic warn"><Icon name="clock" :size="15" /></span>
          <div><b>{{ stats.pending }}</b><span>等待处理</span></div>
        </div>
      </div>
    </section>

    <!-- ======================= 指标卡（点击下钻工单列表） ======================= -->
    <section class="stat-grid">
      <article
        v-for="s in statCards"
        :key="s.key"
        class="stat-card clickable"
        :class="s.tone"
        role="button"
        tabindex="0"
        @click="goCard(s.to)"
        @keyup.enter="goCard(s.to)"
      >
        <div class="stat-top">
          <span class="stat-ic"><Icon :name="s.icon" :size="19" /></span>
          <span class="stat-tag">{{ s.tag }}</span>
        </div>
        <div class="stat-value">{{ s.value }}<small v-if="s.unit">{{ s.unit }}</small></div>
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-foot">
          {{ s.foot }}
          <Icon name="arrowUpRight" :size="12" class="stat-go" />
        </div>
      </article>
    </section>

    <!-- ======================= 图表区 ======================= -->
    <div class="dash-grid">
      <div class="dash-col">
        <section class="panel">
          <div class="panel-hd">
            <div>
              <h3>报修趋势</h3>
              <span class="sb">最近 7 天新增工单 · 共 {{ trendTotal }} 张</span>
            </div>
            <span class="spacer" />
          </div>
          <div class="panel-bd">
            <div class="chart">
              <div class="chart-y">
                <span>{{ trendMax }}</span>
                <span>{{ Math.round(trendMax * 0.5) }}</span>
                <span>0</span>
              </div>
              <div class="bars">
                <div v-for="d in trend" :key="d.key" class="bar-col">
                  <span class="bar-val" :class="{ zero: d.count === 0 }">{{ d.count }}</span>
                  <div class="bar-track">
                    <div
                      class="bar"
                      :class="{ zero: d.count === 0 }"
                      :style="{ height: barHeight(d.count) }"
                    />
                  </div>
                  <span class="bar-label">{{ d.label }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-hd">
            <div>
              <h3>楼栋分布 TOP5</h3>
              <span class="sb">按已完成工单统计</span>
            </div>
            <span class="spacer" />
            <span class="tag tag-ink">区域洞察</span>
          </div>
          <div class="panel-bd">
            <div v-if="buildingRank.length" class="rank-list">
              <div v-for="(c, i) in buildingRank" :key="c.name" class="rank-item">
                <div class="rank-head">
                  <span class="rank-no" :style="{ color: c.color, background: c.soft }">{{ i + 1 }}</span>
                  <span class="rank-name">{{ c.name }}</span>
                  <span class="rank-count">{{ c.count }} 张</span>
                </div>
                <div class="rank-bar">
                  <i :style="{ width: c.pct + '%', background: c.color }" />
                </div>
              </div>
            </div>
            <div v-else class="empty" style="padding: 26px 10px">
              <span class="t">暂无楼栋分布数据</span>
              <span class="d">工单完成后自动按楼栋汇总</span>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-hd">
            <div>
              <h3>最近工单</h3>
              <span class="sb">与你相关的报修记录</span>
            </div>
            <span class="spacer" />
            <button class="btn btn-sm btn-ghost" @click="router.push('/orders')">
              查看全部<Icon name="arrowRight" :size="14" />
            </button>
          </div>

          <div v-if="recent.length" class="recent-list">
            <div
              v-for="o in recent"
              :key="o.id"
              class="recent-item"
              @click="openOrder(o.id)"
            >
              <span class="recent-ic"><Icon :name="categoryIcon(o.categoryName)" :size="17" /></span>
              <div class="recent-main">
                <div class="recent-title">{{ o.title }}</div>
                <div class="recent-meta">
                  <span class="mono">{{ o.orderNo }}</span>
                  <i>·</i>
                  {{ o.locationPath }}
                </div>
              </div>
              <OrderStatusTag :status="o.status" :label="o.statusLabel" />
              <span class="recent-time">{{ shortTime(o.createdAt) }}</span>
            </div>
          </div>
          <div v-else class="empty">
            <span class="ico"><Icon name="receipt" :size="24" /></span>
            <span class="t">还没有工单记录</span>
            <span class="d">点右上角「提交报修」，第一张工单就会出现在这里</span>
          </div>
        </section>
      </div>

      <div class="dash-col">
        <section class="panel">
          <div class="panel-hd">
            <h3>工单状态分布</h3>
            <span class="sb">按当前状态实时统计</span>
          </div>
          <div class="panel-bd donut-wrap">
            <div class="donut" :style="{ background: donutBg }">
              <div class="donut-hole">
                <b>{{ stats.total }}</b>
                <span>工单总数</span>
              </div>
            </div>
            <ul class="legend">
              <li v-for="s in statusSegments" :key="s.name" class="legend-item">
                <span class="dot" :style="{ background: s.color }" />
                <span class="l-name">{{ s.name }}</span>
                <span class="l-val">{{ s.count }}</span>
                <span class="l-pct">{{ s.pct }}%</span>
              </li>
            </ul>
          </div>
        </section>

        <section class="panel">
          <div class="panel-hd">
            <h3>故障类型 TOP5</h3>
            <span class="sb">按报修量排序</span>
          </div>
          <div class="panel-bd">
            <div v-if="categoryRank.length" class="rank-list">
              <div v-for="(c, i) in categoryRank" :key="c.name" class="rank-item">
                <div class="rank-head">
                  <span class="rank-no" :style="{ color: c.color, background: c.soft }">{{ i + 1 }}</span>
                  <span class="rank-name">{{ c.name }}</span>
                  <span class="rank-count">{{ c.count }} 张</span>
                </div>
                <div class="rank-bar">
                  <i :style="{ width: c.pct + '%', background: c.color }" />
                </div>
              </div>
            </div>
            <div v-else class="empty" style="padding: 26px 10px">
              <span class="t">暂无故障类型数据</span>
              <span class="d">提交报修后自动汇总</span>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-hd"><h3>快捷入口</h3></div>
          <div class="panel-bd quick-grid">
            <button class="quick-card" type="button" @click="newOpen = true">
              <span class="q-ic"><Icon name="plus" :size="17" /></span>
              <span class="q-t">提交报修</span>
              <span class="q-d">上传照片，快速建单</span>
            </button>
            <button class="quick-card" type="button" @click="router.push('/orders')">
              <span class="q-ic accent"><Icon name="receipt" :size="17" /></span>
              <span class="q-t">工单管理</span>
              <span class="q-d">跟进处理进度</span>
            </button>
            <button
              v-if="auth.has('user:list')"
              class="quick-card"
              type="button"
              @click="router.push('/users')"
            >
              <span class="q-ic violet"><Icon name="users" :size="17" /></span>
              <span class="q-t">用户管理</span>
              <span class="q-d">账号与角色维护</span>
            </button>
          </div>
        </section>
      </div>
    </div>

    <NewOrderModal :open="newOpen" @close="newOpen = false" @created="onCreated" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../ui/Icon.vue'
import OrderStatusTag from '../ui/OrderStatusTag.vue'
import NewOrderModal from './NewOrderModal.vue'
import StudentHome from './dashboards/StudentHome.vue'
import RepairerHome from './dashboards/RepairerHome.vue'
import { useAuthStore } from '../stores/auth'
import { pageOrders, type OrderVO } from '../api/order'
import {
  getStatsOverview,
  listFaultAlerts,
  updateAlertStatus,
  type FaultAlertVO,
  type StatsOverview
} from '../api/stats'

const auth = useAuthStore()
const router = useRouter()

/** 学生 / 维修人员使用独立的轻量工作台，管理端保持原有总览 */
const isStaffRole = computed(() => auth.hasRole('MANAGER') || auth.hasRole('ADMIN'))
const isRepairerRole = computed(() => !isStaffRole.value && auth.hasRole('REPAIRER'))
const isStudentRole = computed(
  () => !isStaffRole.value && !isRepairerRole.value && auth.hasRole('STUDENT')
)

/* ------------------------------ 数据 ------------------------------ */

const rows = ref<OrderVO[]>([])
const overview = ref<StatsOverview | null>(null)
const alerts = ref<FaultAlertVO[]>([])
const loading = ref(true)
const newOpen = ref(false)

async function load() {
  loading.value = true
  try {
    // 统计走后端聚合（角色数据权限）；最近工单只取 8 条用于展示
    const [ov, recent]: any = await Promise.all([
      getStatsOverview(),
      pageOrders({ page: 1, size: 8 })
    ])
    overview.value = ov
    rows.value = recent?.list || []

    // 重复故障预警：管理端可见（基线 §12.3，只提示不自动处理）
    if (auth.has('order:review') || auth.has('order:assign')) {
      try {
        alerts.value = ((await listFaultAlerts({ status: 'NEW', limit: 5 })) as any) || []
      } catch {
        alerts.value = []
      }
    }
  } catch {
    overview.value = null
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function dismissAlert(a: FaultAlertVO) {
  try {
    await updateAlertStatus(a.id, 'READ')
    alerts.value = alerts.value.filter((x) => x.id !== a.id)
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  // 客户端工作台自行加载数据，避免多打一轮管理端统计接口
  if (!isStudentRole.value && !isRepairerRole.value) load()
})

/* ------------------------------ 角色与问候 ------------------------------ */

const ROLE_LABELS: Record<string, string> = {
  STUDENT: '学生',
  REPAIRER: '维修人员',
  MANAGER: '后勤管理员',
  ADMIN: '系统管理员'
}

const roleText = computed(() => auth.roles.map((r) => ROLE_LABELS[r] || r).join('、') || '—')

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
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

/* ------------------------------ 状态分组 ------------------------------ */

interface StatusGroup {
  name: string
  color: string
  statuses: string[]
}

const STATUS_GROUPS: StatusGroup[] = [
  { name: '待处理', color: '#e63946', statuses: ['PENDING_REVIEW', 'PENDING', 'ASSIGNED'] },
  { name: '处理中', color: '#4f6ef7', statuses: ['ACCEPTED', 'PROCESSING'] },
  { name: '待确认', color: '#f59e0b', statuses: ['PENDING_CONFIRM', 'SUSPENDED'] },
  { name: '已完成', color: '#12a150', statuses: ['COMPLETED'] },
  { name: '已关闭', color: '#9aa3b2', statuses: ['REJECTED', 'CANCELLED'] }
]

const statusCounts = computed(() => overview.value?.statusCounts || {})
const totalCount = computed(() => overview.value?.total || 0)

const countOf = (statuses: string[]) =>
  statuses.reduce((n, s) => n + (Number(statusCounts.value[s]) || 0), 0)

const statusSegments = computed(() =>
  STATUS_GROUPS.map((g) => {
    const count = countOf(g.statuses)
    return {
      ...g,
      count,
      pct: totalCount.value ? Math.round((count / totalCount.value) * 100) : 0
    }
  })
)

const stats = computed(() => {
  const seg = (name: string) => statusSegments.value.find((s) => s.name === name)?.count || 0
  const pending = seg('待处理')
  const active = seg('处理中') + seg('待确认')
  const done = seg('已完成')
  const closed = seg('已关闭')
  const completed = overview.value?.completed
  return {
    total: totalCount.value,
    pending,
    active,
    done,
    closed,
    review: Number(statusCounts.value.PENDING_REVIEW) || 0,
    confirm: Number(statusCounts.value.PENDING_CONFIRM) || 0,
    rate: totalCount.value ? Math.round((done / totalCount.value) * 100) : 0,
    doneToday: completed?.today || 0,
    doneWeek: completed?.week || 0,
    doneMonth: completed?.month || 0,
    avgHandle: overview.value?.avgHandleMinutes || 0,
    avgRepair: overview.value?.avgRepairMinutes || 0
  }
})

const heroLine = computed(() =>
  stats.value.pending > 0
    ? `有 ${stats.value.pending} 张工单等待处理`
    : '今天没有待处理工单'
)

const fmtDuration = (m: number) => (m >= 60 ? `${Math.floor(m / 60)}小时${m % 60}分` : `${m}分钟`)

/** KPI 卡下钻目标：跳工单管理并自动应用多状态筛选（后端 parseStatuses 逗号解析） */
const STATUS_WAITING = 'PENDING_REVIEW,PENDING,ASSIGNED'
const STATUS_ACTIVE = 'ACCEPTED,PROCESSING,SUSPENDED,PENDING_CONFIRM'

const statCards = computed(() => [
  {
    key: 'total',
    tone: 'brand',
    icon: 'receipt',
    tag: '累计',
    value: stats.value.total,
    unit: '张',
    label: '工单总数',
    foot: `本月完成 ${stats.value.doneMonth} 张`,
    to: '/orders'
  },
  {
    key: 'pending',
    tone: 'warn',
    icon: 'clock',
    tag: '待办',
    value: stats.value.pending,
    unit: '张',
    label: '等待处理',
    foot: `其中待审核 ${stats.value.review} 张`,
    to: `/orders?status=${STATUS_WAITING}`
  },
  {
    key: 'active',
    tone: 'accent',
    icon: 'bolt',
    tag: '进行中',
    value: stats.value.active,
    unit: '张',
    label: '进行中的工单',
    foot: `今日完成 ${stats.value.doneToday} 张`,
    to: `/orders?status=${STATUS_ACTIVE}`
  },
  {
    key: 'done',
    tone: 'ok',
    icon: 'check',
    tag: '效率',
    value: stats.value.avgHandle > 0 ? fmtDuration(stats.value.avgHandle) : '—',
    unit: '',
    label: '平均处理时长',
    foot: `平均维修 ${stats.value.avgRepair > 0 ? fmtDuration(stats.value.avgRepair) : '—'} · 已完成 ${stats.value.done} 张`,
    to: '/orders?status=COMPLETED'
  }
])

function goCard(to: string) {
  const [path, query] = to.split('?')
  router.push({ path, query: query ? Object.fromEntries(new URLSearchParams(query)) : {} })
}

/* ------------------------------ 趋势图 ------------------------------ */

/** 后端返回 {date, count}，映射为图表需要的 {key, label, count} */
const trend = computed(() =>
  (overview.value?.trend || []).map((t) => ({
    key: t.date,
    label: t.date.replace('-', '/'),
    count: t.count
  }))
)

const trendMax = computed(() => Math.max(1, ...trend.value.map((d) => d.count)))
const trendTotal = computed(() => trend.value.reduce((sum, d) => sum + d.count, 0))

function barHeight(count: number) {
  if (count <= 0) return '4%'
  return `${Math.max(10, Math.round((count / trendMax.value) * 100))}%`
}

/* ------------------------------ 环形图 ------------------------------ */

const donutBg = computed(() => {
  if (!totalCount.value) return 'conic-gradient(#eef0f5 0 100%)'
  let acc = 0
  const parts: string[] = []
  statusSegments.value.forEach((s) => {
    if (s.count <= 0) return
    const from = acc
    acc += (s.count / totalCount.value) * 100
    parts.push(`${s.color} ${from.toFixed(2)}% ${acc.toFixed(2)}%`)
  })
  if (!parts.length) return 'conic-gradient(#eef0f5 0 100%)'
  if (acc < 100) parts.push(`#eef0f5 ${acc.toFixed(2)}% 100%`)
  return `conic-gradient(${parts.join(', ')})`
})

/* ------------------------------ 排行（类型 / 楼栋） ------------------------------ */

/* 排行条为纯装饰性配色（非状态语义），统一走石板灰系，靠名次与长度区分 */
const RANK_TONE = { color: 'var(--slate-500)', soft: 'var(--slate-100)' }

const toRank = (items: { name: string; count: number }[]) => {
  const max = Math.max(1, ...items.map((i) => i.count))
  return items.map((it) => ({
    name: it.name,
    count: it.count,
    pct: Math.max(6, Math.round((it.count / max) * 100)),
    color: RANK_TONE.color,
    soft: RANK_TONE.soft
  }))
}

const categoryRank = computed(() => toRank(overview.value?.categoryTop || []))
const buildingRank = computed(() => toRank(overview.value?.buildingTop || []))

/* ------------------------------ 最近工单 ------------------------------ */

const recent = computed(() => rows.value.slice(0, 8))

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

function categoryIcon(name?: string) {
  if (!name) return 'wrench'
  if (name.includes('空调')) return 'sparkles'
  if (name.includes('水') || name.includes('电')) return 'bolt'
  if (name.includes('门') || name.includes('锁')) return 'lock'
  if (name.includes('网络')) return 'grid'
  return 'wrench'
}

function openOrder(id: number) {
  router.push({ path: '/orders', query: { id: String(id) } })
}

function onCreated() {
  load()
  router.push('/orders')
}
</script>

<style scoped>
/* ------------------------------------------------ 布局 */
.dash {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ------------------------------------------------ Hero */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  padding: 30px 34px;
  color: #fff;
  background:
    radial-gradient(760px 420px at 92% -30%, rgba(100, 116, 139, 0.5), transparent 62%),
    radial-gradient(520px 360px at 4% 130%, rgba(71, 85, 105, 0.42), transparent 62%),
    linear-gradient(125deg, #0f172a 0%, #1e293b 48%, #0b1220 100%);
  border: 1px solid rgba(255, 255, 255, 0.07);
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.26);
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(300px, 0.72fr);
  gap: 18px;
  align-items: center;
}
.hero::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, .04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .04) 1px, transparent 1px);
  background-size: 58px 58px;
  mask-image: radial-gradient(ellipse at 24% 30%, #000 0%, transparent 74%);
  -webkit-mask-image: radial-gradient(ellipse at 24% 30%, #000 0%, transparent 74%);
}
.hero-main { position: relative; z-index: 1; min-width: 0; }
.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #c8ccd6;
  background: rgba(255, 255, 255, .07);
  border: 1px solid rgba(255, 255, 255, .08);
  padding: 6px 12px;
  border-radius: 20px;
}
.hero-eyebrow .pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--slate-400);
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.22);
  animation: pulse 2.2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.22); }
  50% { box-shadow: 0 0 0 7px rgba(148, 163, 184, 0.02); }
}
.hero-title {
  font-size: 31px;
  line-height: 1.3;
  font-weight: 800;
  letter-spacing: -0.8px;
  margin: 18px 0 0;
}
.hero-title em {
  display: block;
  font-style: normal;
  font-size: 23px;
  font-weight: 700;
  letter-spacing: -0.4px;
  margin-top: 4px;
  background: linear-gradient(100deg, #f8fafc, #cbd5e1 55%, #94a3b8);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero-sub {
  margin-top: 12px;
  max-width: 520px;
  color: var(--slate-400);
  font-size: 13.5px;
  line-height: 1.8;
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 22px;
  flex-wrap: wrap;
}
.hero-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 11px;
  background: var(--slate-50);
  color: var(--slate-900);
  border: none;
  font-weight: 600;
  box-shadow: 0 10px 24px rgba(2, 6, 23, 0.34);
}
.hero-btn:hover {
  background: #fff;
  color: var(--slate-900);
  border: none;
  box-shadow: 0 14px 30px rgba(2, 6, 23, 0.44);
}
.hero-btn-ghost {
  height: 40px;
  padding: 0 18px;
  border-radius: 11px;
  background: rgba(255, 255, 255, .07);
  border: 1px solid rgba(255, 255, 255, .14);
  color: #e8ebf2;
}
.hero-btn-ghost:hover {
  background: rgba(255, 255, 255, .13);
  border-color: rgba(255, 255, 255, .22);
  color: #fff;
}
.hero-sync {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  color: #9aa2b2;
}
.hero-sync .spin {
  width: 13px;
  height: 13px;
  border-width: 2px;
  border-color: rgba(255, 255, 255, .2);
  border-top-color: #fff;
}
.hero-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, .08);
  font-size: 11.5px;
  color: #98a0b0;
}
.hero-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.hero-meta .ui-ic { color: #ff9a8a; }

/* Hero 插画 */
.hero-art {
  position: relative;
  z-index: 1;
  min-height: 220px;
  display: grid;
  place-items: center;
}
.art-svg { width: 100%; max-width: 360px; height: auto; filter: drop-shadow(0 18px 30px rgba(0, 0, 0, .2)); }
.float-card {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 13px 9px 9px;
  border-radius: 13px;
  background: rgba(255, 255, 255, .1);
  border: 1px solid rgba(255, 255, 255, .16);
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, .22);
  animation: floatUp 4.6s ease-in-out infinite;
}
.float-card b {
  display: block;
  font-size: 15px;
  line-height: 1.15;
  letter-spacing: -0.3px;
}
.float-card span {
  font-size: 10.5px;
  color: #b9c0cc;
}
.fc-1 { right: 4%; bottom: 8%; animation-delay: 0.4s; }
.fc-2 { left: 0; top: 10%; animation-delay: 1.4s; }
.fc-ic {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  flex: none;
}
.fc-ic.ok { background: rgba(255, 255, 255, 0.15); color: var(--slate-200); }
.fc-ic.warn { background: rgba(255, 255, 255, 0.1); color: var(--slate-300); }
@keyframes floatUp {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-7px); }
}

/* ------------------------------------------------ 预警横幅 */
.alert-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #fff7ed, #fef2f2 70%);
  border: 1px solid #fde3c8;
}
.ab-ic {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: #f59e0b;
  color: #fff;
  box-shadow: 0 6px 14px rgba(245, 158, 11, 0.3);
}
.ab-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ab-body b {
  font-size: 12.5px;
  color: #9a3412;
}
.ab-item {
  font-size: 12.5px;
  color: var(--ink-2);
  line-height: 1.55;
}
.ab-btn {
  flex: none;
  height: 32px;
  padding: 0 14px;
  border-radius: 9px;
  border: 1px solid #f0d5ac;
  background: #fff;
  color: #a2630a;
  font-size: 12.5px;
  cursor: pointer;
}
.ab-btn:hover {
  background: #fef4e2;
}

.stat-card.clickable {
  cursor: pointer;
}
.stat-card.clickable:hover {
  border-color: #cfd6e2;
}
.stat-foot .stat-go {
  float: right;
  margin-top: 1px;
  color: var(--ink-3);
  transition: transform 0.16s, color 0.16s;
}
.stat-card.clickable:hover .stat-go {
  color: var(--slate-500);
  transform: translate(1px, -1px);
}
.float-card.clickable {
  cursor: pointer;
  transition: background 0.16s, border-color 0.16s, transform 0.16s;
}
.float-card.clickable:hover {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.3);
}

/* ------------------------------------------------ 指标卡 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stat-card {
  position: relative;
  overflow: hidden;
  background: #fff;
  border-radius: 16px;
  border: 1px solid rgba(230, 232, 239, 0.85);
  box-shadow: var(--sh-1);
  padding: 18px 19px 16px;
  transition: transform 0.18s, box-shadow 0.18s;
}
.stat-card::after {
  content: "";
  position: absolute;
  right: -26px;
  top: -30px;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.06;
  pointer-events: none;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--sh-2);
}
/* 指标卡配色统一为石板灰系：以明度差而不是色相差区分卡片 */
.stat-card.brand,
.stat-card.warn,
.stat-card.accent,
.stat-card.ok { color: var(--slate-500); }
.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-ic {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
}
.stat-card.brand .stat-ic,
.stat-card.warn .stat-ic,
.stat-card.accent .stat-ic,
.stat-card.ok .stat-ic { background: var(--slate-100); color: var(--slate-600); }
.stat-tag {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.4px;
  padding: 4px 8px;
  border-radius: 7px;
  background: #f5f6fa;
  color: var(--ink-3);
}
.stat-value {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -1.2px;
  line-height: 1.15;
  color: var(--ink);
  margin-top: 14px;
}
.stat-value small {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-3);
  margin-left: 4px;
  letter-spacing: 0;
}
.stat-label {
  font-size: 12.5px;
  color: var(--ink-2);
  font-weight: 500;
  margin-top: 1px;
}
.stat-foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  font-size: 11px;
  color: var(--ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ------------------------------------------------ 图表区 */
.dash-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(318px, 0.82fr);
  gap: 16px;
  align-items: start;
}
.dash-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.panel-hd > div { display: flex; align-items: baseline; gap: 9px; flex-wrap: wrap; }

/* 柱状图 */
.chart {
  display: grid;
  grid-template-columns: 26px 1fr;
  gap: 10px;
  padding-top: 6px;
}
.chart-y {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  height: 172px;
  padding-bottom: 26px;
  font-size: 10.5px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}
.bars {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
  align-items: end;
  position: relative;
}
.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.bar-val {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.bar-val.zero { color: #c3c8d2; font-weight: 500; }
.bar-track {
  width: 100%;
  height: 146px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  border-bottom: 1px solid var(--line-2);
  background-image: repeating-linear-gradient(
    to top,
    transparent 0,
    transparent calc(25% - 1px),
    var(--line-2) calc(25% - 1px),
    var(--line-2) 25%
  );
}
.bar {
  width: min(38px, 72%);
  border-radius: 8px 8px 0 0;
  background: linear-gradient(180deg, var(--slate-400) 0%, var(--slate-500) 70%, var(--slate-600) 100%);
  box-shadow: 0 -4px 14px rgba(71, 85, 105, 0.18);
  transition: height 0.5s cubic-bezier(0.2, 0.9, 0.3, 1), filter 0.15s;
  min-height: 4px;
}
.bar:hover { filter: brightness(1.06); }
.bar.zero {
  background: #eceef4;
  box-shadow: none;
  border-radius: 4px;
}
.bar-label {
  font-size: 10.5px;
  color: var(--ink-3);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 环形图 */
.donut-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding-top: 24px;
  padding-bottom: 22px;
}
.donut {
  width: 172px;
  height: 172px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  position: relative;
  transition: background 0.4s ease;
  filter: drop-shadow(0 10px 24px rgba(20, 24, 40, 0.09));
}
.donut::before {
  content: "";
  position: absolute;
  inset: -7px;
  border-radius: 50%;
  border: 1px dashed var(--line);
  opacity: 0.8;
}
.donut-hole {
  width: 112px;
  height: 112px;
  border-radius: 50%;
  background: #fff;
  display: grid;
  place-items: center;
  align-content: center;
  box-shadow: inset 0 2px 10px rgba(20, 24, 40, 0.06);
}
.donut-hole b {
  font-size: 27px;
  font-weight: 800;
  letter-spacing: -1px;
  line-height: 1.15;
  color: var(--ink);
}
.donut-hole span {
  font-size: 10.5px;
  color: var(--ink-3);
  margin-top: 2px;
}
.legend {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  padding: 0 4px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  min-width: 0;
}
.legend-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.legend .l-name { color: var(--ink-2); white-space: nowrap; }
.legend .l-val {
  margin-left: auto;
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}
.legend .l-pct { color: var(--ink-3); font-size: 10px; width: 30px; text-align: right; }

/* 故障排行 */
.rank-list { display: flex; flex-direction: column; gap: 15px; }
.rank-item { display: flex; flex-direction: column; gap: 7px; }
.rank-head { display: flex; align-items: center; gap: 8px; }
.rank-no {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  font-size: 10.5px;
  font-weight: 800;
  flex: none;
}
.rank-name {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-count {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.rank-bar {
  height: 7px;
  border-radius: 5px;
  background: var(--line-2);
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  border-radius: 5px;
  transition: width 0.5s cubic-bezier(0.2, 0.9, 0.3, 1);
}

/* 最近工单 */
.recent-list { padding: 8px; }
.recent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.14s;
}
.recent-item:hover { background: #fafbfd; }
.recent-ic {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: var(--slate-100);
  color: var(--slate-600);
  flex: none;
}
.recent-main { flex: 1; min-width: 0; }
.recent-title {
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-meta {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-meta .mono { font-family: ui-monospace, "Cascadia Mono", Consolas, monospace; font-size: 11.5px; }
.recent-meta i { font-style: normal; margin: 0 4px; color: #c3c8d2; }
.recent-time {
  font-size: 11px;
  color: var(--ink-3);
  white-space: nowrap;
  min-width: 66px;
  text-align: right;
}

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: #fff;
  text-align: left;
  transition: border-color 0.16s, background 0.16s, transform 0.14s, box-shadow 0.16s;
}
.quick-card:hover {
  border-color: #cfd6e2;
  background: #fbfcfe;
  transform: translateY(-2px);
  box-shadow: var(--sh-1);
}
/* 卡片数为奇数时最后一张通栏，避免右下角留空、保持版面平衡 */
.quick-card:last-child:nth-child(odd) {
  grid-column: 1 / -1;
  flex-direction: row;
  align-items: center;
  gap: 11px;
  padding: 12px 14px;
}
.quick-card:last-child:nth-child(odd) .q-t { margin-right: auto; }
.quick-card:last-child:nth-child(odd) .q-d { text-align: right; }
.q-ic {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--slate-100);
  color: var(--slate-600);
  margin-bottom: 2px;
}
/* 快捷入口属于装饰性图标，统一走石板灰系；保留修饰类名以兼容模板 */
.q-ic.accent,
.q-ic.violet { background: var(--slate-100); color: var(--slate-600); }
.q-t { font-size: 13px; font-weight: 600; color: var(--ink); }
.q-d { font-size: 10.5px; color: var(--ink-3); line-height: 1.5; }

/* ------------------------------------------------ 响应式 */
@media (max-width: 1250px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .dash-grid { grid-template-columns: 1fr; }
  .hero { grid-template-columns: minmax(0, 1.25fr) minmax(250px, 0.6fr); }
}
@media (max-width: 1080px) {
  .hero { grid-template-columns: 1fr; padding: 26px 26px 24px; }
  .hero-art { display: none; }
}
@media (max-width: 720px) {
  .eyebrow-date { display: none; }
  .stat-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
  .stat-card { padding: 14px; }
  .stat-value { font-size: 25px; }
  .stat-foot { font-size: 10.5px; }
  .hero { padding: 22px 20px; border-radius: 20px; }
  .hero-title { font-size: 25px; }
  .hero-title em { font-size: 19px; }
  .legend { grid-template-columns: 1fr; }
  .recent-time { display: none; }
  .quick-grid { grid-template-columns: 1fr 1fr; }
}
</style>
